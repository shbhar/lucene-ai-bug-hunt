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
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.CollectorManager;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * Weight#scorerSupplier "must return null if the scorer is null". CompletionWeight always returns
 * a supplier, and that supplier's bulkScorer() returns null on a segment without the suggest
 * field (or without an FST, or when the filter matches nothing). SuggestIndexSearcher#suggest
 * goes through Weight#bulkScorer and skips the null, but IndexSearcher#searchLeaf calls
 * scorerSupplier.bulkScorer() and uses the result without a null check, so IndexSearcher#search
 * of a CompletionQuery over an index with such a segment throws NullPointerException.
 *
 * <p>Release-portable variant (9.x and 10.x): only the IndexSearcher#search leg. Public API only.
 */
public class TestCompletionWeightNullBulkScorer9x extends LuceneTestCase {

  private static final int TOP_N = 5;

  private Directory dir;
  private DirectoryReader reader;
  private Analyzer analyzer;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    analyzer = new MockAnalyzer(random(), MockTokenizer.KEYWORD, false);
    IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
    iwc.setMergePolicy(NoMergePolicy.INSTANCE);
    final Codec defaultCodec = TestUtil.getDefaultCodec();
    iwc.setCodec(
        new FilterCodec(defaultCodec.getName(), defaultCodec) {
          final PostingsFormat completion = newestCompletionFormat();

          @Override
          public PostingsFormat postingsFormat() {
            return new PerFieldPostingsFormat() {
              @Override
              public PostingsFormat getPostingsFormatForField(String field) {
                if (field.equals("sug")) {
                  return completion;
                }
                return ((PerFieldPostingsFormat) delegate.postingsFormat())
                    .getPostingsFormatForField(field);
              }
            };
          }
        });
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      // segment 1: suggestions
      for (int i = 0; i < 3; i++) {
        Document doc = new Document();
        doc.add(new SuggestField("sug", "apple" + i, i + 1));
        w.addDocument(doc);
      }
      w.commit();
      // segment 2: a document without the suggest field
      Document doc = new Document();
      doc.add(new StringField("other", "x", Field.Store.NO));
      w.addDocument(doc);
      w.commit();
    }
    reader = DirectoryReader.open(dir);
    assertEquals(2, reader.leaves().size());
  }

  private static PostingsFormat newestCompletionFormat() {
    for (String name :
        new String[] {"Completion104", "Completion101", "Completion912", "Completion99", "Completion90"}) {
      try {
        return PostingsFormat.forName(name);
      } catch (IllegalArgumentException e) {
        // not in this release
      }
    }
    throw new AssertionError("no completion postings format");
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    analyzer.close();
    super.tearDown();
  }

  private CompletionQuery query() {
    return new PrefixCompletionQuery(analyzer, new Term("sug", "apple"));
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
        List<TopSuggestDocs> all = new ArrayList<>();
        for (TopSuggestDocsCollector c : collectors) {
          all.add(c.get());
        }
        return TopSuggestDocs.merge(n, all.toArray(new TopSuggestDocs[0]));
      }
    };
  }

  private static List<String> hits(TopSuggestDocs docs) {
    List<String> out = new ArrayList<>();
    for (TopSuggestDocs.SuggestScoreDoc sd : docs.scoreLookupDocs()) {
      out.add(sd.doc + "/" + sd.key + "/" + sd.score);
    }
    return out;
  }

  /** IndexSearcher#search gives the same suggestions as SuggestIndexSearcher#suggest. */
  public void testSegmentWithoutSuggestFieldIsSkipped() throws IOException {
    List<String> expected = hits(new SuggestIndexSearcher(reader).suggest(query(), TOP_N, false));
    assertEquals(3, expected.size());
    IndexSearcher searcher = new IndexSearcher(reader);
    assertEquals(expected, hits(searcher.search(query(), manager(TOP_N))));
  }
}
