/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.search.suggest.document;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.perfield.PerFieldPostingsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.CollectorManager;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.ScorerSupplier;
import org.apache.lucene.search.Weight;
import org.apache.lucene.search.suggest.BitsProducer;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.Bits;

/**
 * Runs a {@link CompletionQuery} through the generic IndexSearcher#search(Query, CollectorManager)
 * with {@link TopSuggestDocsCollector}s and expects the same suggestions as {@link
 * SuggestIndexSearcher#suggest(CompletionQuery, int, boolean)}, also when a segment cannot match.
 */
public class TestCompletionQueryWithIndexSearcher extends LuceneTestCase {

  private static final String FIELD = "suggest_field";

  private Directory dir;
  private Analyzer analyzer;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    analyzer = new MockAnalyzer(random());
  }

  @Override
  public void tearDown() throws Exception {
    analyzer.close();
    dir.close();
    super.tearDown();
  }

  /** Default codec with the completion postings format for FIELD; segments exactly as committed. */
  private IndexWriter newWriter() throws IOException {
    final Codec codec = TestUtil.getDefaultCodec();
    final PostingsFormat completion = new Completion104PostingsFormat();
    IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
    iwc.setMergePolicy(NoMergePolicy.INSTANCE);
    iwc.setCodec(
        new FilterCodec(codec.getName(), codec) {
          @Override
          public PostingsFormat postingsFormat() {
            return new PerFieldPostingsFormat() {
              @Override
              public PostingsFormat getPostingsFormatForField(String field) {
                return FIELD.equals(field)
                    ? completion
                    : ((PerFieldPostingsFormat) delegate.postingsFormat())
                        .getPostingsFormatForField(field);
              }
            };
          }
        });
    return new IndexWriter(dir, iwc);
  }

  private static void addSuggestions(IndexWriter w) throws IOException {
    for (int i = 0; i < 3; i++) {
      Document doc = new Document();
      doc.add(new SuggestField(FIELD, "apple" + i, i + 1));
      w.addDocument(doc);
    }
  }

  /** A segment with 3 suggestions, then a segment whose only document has no suggest field. */
  private DirectoryReader indexWithSegmentWithoutSuggestField() throws IOException {
    try (IndexWriter w = newWriter()) {
      addSuggestions(w);
      w.commit();
      Document doc = new Document();
      doc.add(new StringField("id", "no_suggestion", Field.Store.NO));
      w.addDocument(doc);
      w.commit();
    }
    DirectoryReader reader = DirectoryReader.open(dir);
    assertEquals(2, reader.leaves().size());
    return reader;
  }

  private PrefixCompletionQuery query(BitsProducer filter) {
    return new PrefixCompletionQuery(analyzer, new Term(FIELD, "app"), filter);
  }

  private static CollectorManager<TopSuggestDocsCollector, TopSuggestDocs> manager(int n) {
    return new CollectorManager<>() {
      @Override
      public TopSuggestDocsCollector newCollector() {
        return new TopSuggestDocsCollector(n, false);
      }

      @Override
      public TopSuggestDocs reduce(Collection<TopSuggestDocsCollector> collectors)
          throws IOException {
        List<TopSuggestDocs> hits = new ArrayList<>();
        for (TopSuggestDocsCollector collector : collectors) {
          hits.add(collector.get());
        }
        return TopSuggestDocs.merge(n, hits.toArray(new TopSuggestDocs[0]));
      }
    };
  }

  private static List<String> hits(TopSuggestDocs docs) {
    List<String> hits = new ArrayList<>();
    for (TopSuggestDocs.SuggestScoreDoc hit : docs.scoreLookupDocs()) {
      hits.add(hit.key + "/doc=" + hit.doc + "/score=" + hit.score);
    }
    return hits;
  }

  public void testSegmentWithoutSuggestField() throws IOException {
    try (DirectoryReader reader = indexWithSegmentWithoutSuggestField()) {
      List<String> expected = hits(new SuggestIndexSearcher(reader).suggest(query(null), 5, false));
      assertEquals(3, expected.size());
      IndexSearcher searcher = new IndexSearcher(reader);
      assertEquals(expected, hits(searcher.search(query(null), manager(5))));
    }
  }

  public void testSegmentWithoutSuggestFieldWithTimeout() throws IOException {
    try (DirectoryReader reader = indexWithSegmentWithoutSuggestField()) {
      List<String> expected = hits(new SuggestIndexSearcher(reader).suggest(query(null), 5, false));
      IndexSearcher searcher = new IndexSearcher(reader);
      searcher.setTimeout(() -> false); // never fires
      List<String> actual = hits(searcher.search(query(null), manager(5)));
      // compare distinct hits only: repeated hits under a timeout are a separate issue
      assertEquals(expected, new ArrayList<>(new LinkedHashSet<>(actual)));
    }
  }

  public void testFilterMatchingNoDocumentOnSingleSegment() throws IOException {
    try (IndexWriter w = newWriter()) {
      addSuggestions(w);
    }
    try (DirectoryReader reader = DirectoryReader.open(dir)) {
      assertEquals(1, reader.leaves().size());
      BitsProducer matchNone =
          new BitsProducer() {
            @Override
            public Bits getBits(LeafReaderContext context) {
              return new Bits.MatchNoBits(context.reader().maxDoc());
            }
          };
      List<String> expected =
          hits(new SuggestIndexSearcher(reader).suggest(query(matchNone), 5, false));
      assertEquals(0, expected.size());
      IndexSearcher searcher = new IndexSearcher(reader);
      assertEquals(expected, hits(searcher.search(query(matchNone), manager(5))));
    }
  }

  /** Weight#scorerSupplier "must return null if the scorer is null". */
  public void testNoScorerSupplierWhenNothingCanMatch() throws IOException {
    try (DirectoryReader reader = indexWithSegmentWithoutSuggestField()) {
      IndexSearcher searcher = new IndexSearcher(reader);
      Weight weight = searcher.createWeight(searcher.rewrite(query(null)), ScoreMode.COMPLETE, 1f);
      for (LeafReaderContext leaf : reader.leaves()) {
        ScorerSupplier supplier = weight.scorerSupplier(leaf);
        if (supplier != null) {
          assertNotNull(
              "leaf " + leaf.ord + ": non-null ScorerSupplier whose bulkScorer() is null",
              supplier.bulkScorer());
        }
      }
    }
  }
}
