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
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.QueryTimeout;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.CollectorManager;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * BulkScorer#score(collector, acceptDocs, min, max) must collect only the matches in [min, max).
 * CompletionScorer ignores min and max and runs the whole-leaf lookup on every call. IndexSearcher
 * scores each leaf in growing windows when a QueryTimeout is set, and in partitions when
 * intra-segment slices are configured, so searching a CompletionQuery through IndexSearcher#search
 * with a TopSuggestDocsCollector then returns duplicated suggestions and drops others, silently.
 *
 * <p>Uses public API only (the package matches upstream's suggest tests).
 */
public class TestCompletionScorerWindows extends LuceneTestCase {

  private static final int NUM_DOCS = 500;
  private static final int TOP_N = 5;

  private Directory dir;
  private DirectoryReader reader;
  private Analyzer analyzer;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    analyzer = new MockAnalyzer(random(), MockTokenizer.KEYWORD, false);
    IndexWriterConfig iwc = suggestConfig(analyzer);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < NUM_DOCS; i++) {
        Document doc = new Document();
        doc.add(new SuggestField("sug", "apple" + i, i + 1));
        w.addDocument(doc);
      }
      w.forceMerge(1);
    }
    reader = DirectoryReader.open(dir);
    assertEquals(1, reader.leaves().size());
  }

  private static IndexWriterConfig suggestConfig(Analyzer analyzer) {
    IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
    final Codec defaultCodec = TestUtil.getDefaultCodec();
    iwc.setCodec(
        new FilterCodec(defaultCodec.getName(), defaultCodec) {
          final PostingsFormat completion = new Completion104PostingsFormat();

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
    return iwc;
  }

  private static QueryTimeout neverFires() {
    return new QueryTimeout() {
      @Override
      public boolean shouldExit() {
        return false;
      }
    };
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

  private List<String> expected() throws IOException {
    return hits(new SuggestIndexSearcher(reader).suggest(query(), TOP_N, false));
  }

  /** Control: without a timeout, IndexSearcher#search gives the same suggestions as suggest(). */
  public void testPlainIndexSearcherMatchesSuggest() throws IOException {
    IndexSearcher searcher = new IndexSearcher(reader);
    assertEquals(expected(), hits(searcher.search(query(), manager(TOP_N))));
  }

  /** Minimal case: one document, one suggestion, a QueryTimeout that never fires. */
  public void testOneSuggestionUnderTimeoutIsReturnedOnce() throws IOException {
    try (Directory oneDir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(oneDir, suggestConfig(analyzer))) {
        Document doc = new Document();
        doc.add(new SuggestField("sug", "apple", 7));
        w.addDocument(doc);
      }
      try (DirectoryReader oneReader = DirectoryReader.open(oneDir)) {
        IndexSearcher searcher = new IndexSearcher(oneReader);
        searcher.setTimeout(neverFires());
        TopSuggestDocs actual = searcher.search(query(), manager(TOP_N));
        assertEquals(
            hits(new SuggestIndexSearcher(oneReader).suggest(query(), TOP_N, false)), hits(actual));
      }
    }
  }

  /** A QueryTimeout that never fires must not change the result. */
  public void testNeverFiringTimeoutKeepsSuggestions() throws IOException {
    IndexSearcher searcher = new IndexSearcher(reader);
    searcher.setTimeout(neverFires());
    TopSuggestDocs actual = searcher.search(query(), manager(TOP_N));
    assertFalse(searcher.timedOut());
    assertEquals(expected(), hits(actual));
  }

  /** Splitting the segment into intra-segment partitions must not change the result. */
  public void testIntraSegmentPartitionsKeepSuggestions() throws IOException {
    IndexSearcher searcher =
        new IndexSearcher(reader, Runnable::run) {
          @Override
          protected LeafSlice[] slices(List<LeafReaderContext> leaves) {
            return slices(leaves, 100, 1, true);
          }
        };
    assertTrue(searcher.getSlices().length > 1);
    assertEquals(expected(), hits(searcher.search(query(), manager(TOP_N))));
  }
}
