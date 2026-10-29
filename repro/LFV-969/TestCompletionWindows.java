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
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.perfield.PerFieldPostingsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BulkScorer;
import org.apache.lucene.search.CollectorManager;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * Independent check: a CompletionQuery executed through the generic IndexSearcher path, with a
 * timeout that never fires, should return the same suggestions as SuggestIndexSearcher#suggest;
 * and a CompletionScorer asked for a doc-id window [min, max) should not collect docs outside it
 * (BulkScorer#score javadoc).
 *
 * <p>Only public API plus the same package as Lucene's own suggest tests. Release-portable (9.4+).
 */
public class TestCompletionWindows extends LuceneTestCase {

  static final String FIELD = "sug";

  /** Completion postings format that wraps the default codec's postings format, found by SPI. */
  static Codec completionCodec() {
    Codec base = TestUtil.getDefaultCodec();
    String defaultPf =
        ((PerFieldPostingsFormat) base.postingsFormat()).getPostingsFormatForField(FIELD).getName();
    final PostingsFormat completion =
        PostingsFormat.forName("Completion" + defaultPf.substring("Lucene".length()));
    return new FilterCodec(base.getName(), base) {
      @Override
      public PostingsFormat postingsFormat() {
        return new PerFieldPostingsFormat() {
          @Override
          public PostingsFormat getPostingsFormatForField(String field) {
            return completion;
          }
        };
      }
    };
  }

  /** One segment; doc i has the single suggestion "apple" + i with weight i + 1. */
  static Directory index(int numDocs, Analyzer analyzer) throws IOException {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig(analyzer).setCodec(completionCodec());
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < numDocs; i++) {
        Document doc = new Document();
        doc.add(new SuggestField(FIELD, "apple" + i, i + 1));
        w.addDocument(doc);
      }
      w.forceMerge(1);
    }
    return dir;
  }

  static CollectorManager<TopSuggestDocsCollector, TopSuggestDocs> manager(
      int n, boolean skipDuplicates) {
    return new CollectorManager<TopSuggestDocsCollector, TopSuggestDocs>() {
      @Override
      public TopSuggestDocsCollector newCollector() {
        return new TopSuggestDocsCollector(n, skipDuplicates);
      }

      @Override
      public TopSuggestDocs reduce(Collection<TopSuggestDocsCollector> collectors)
          throws IOException {
        TopSuggestDocs[] parts = new TopSuggestDocs[collectors.size()];
        int i = 0;
        for (TopSuggestDocsCollector c : collectors) {
          parts[i++] = c.get();
        }
        return TopSuggestDocs.merge(n, parts);
      }
    };
  }

  static List<String> render(TopSuggestDocs docs) {
    List<String> out = new ArrayList<>();
    for (TopSuggestDocs.SuggestScoreDoc d : docs.scoreLookupDocs()) {
      out.add(d.doc + ":" + d.key + ":" + d.score);
    }
    return out;
  }

  static PrefixCompletionQuery query(Analyzer analyzer) {
    return new PrefixCompletionQuery(analyzer, new Term(FIELD, "app"));
  }

  /** Control: the generic path without a timeout agrees with suggest(). */
  public void testControlGenericPathWithoutTimeout() throws Exception {
    Analyzer a = new StandardAnalyzer();
    try (Directory dir = index(300, a);
        DirectoryReader r = DirectoryReader.open(dir)) {
      List<String> expected = render(new SuggestIndexSearcher(r).suggest(query(a), 5, false));
      assertEquals(5, expected.size());
      IndexSearcher s = new IndexSearcher(r);
      assertEquals(expected, render(s.search(query(a), manager(5, false))));
    }
    a.close();
  }

  /** A timeout that never fires must not change the result. */
  public void testNeverFiringTimeoutKeepsTopSuggestions() throws Exception {
    Analyzer a = new StandardAnalyzer();
    try (Directory dir = index(300, a);
        DirectoryReader r = DirectoryReader.open(dir)) {
      List<String> expected = render(new SuggestIndexSearcher(r).suggest(query(a), 5, false));
      IndexSearcher s = new IndexSearcher(r);
      s.setTimeout(() -> false);
      List<String> actual = render(s.search(query(a), manager(5, false)));
      assertFalse(s.timedOut());
      assertEquals(expected, actual);
    }
    a.close();
  }

  /** One document, one suggestion, never-firing timeout: exactly one hit expected. */
  public void testSingleSuggestionUnderTimeoutReturnedOnce() throws Exception {
    Analyzer a = new StandardAnalyzer();
    try (Directory dir = index(1, a);
        DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = new IndexSearcher(r);
      s.setTimeout(() -> false);
      List<String> actual = render(s.search(query(a), manager(5, false)));
      assertEquals(List.of("0:apple0:1.0"), actual);
    }
    a.close();
  }

  /** Observation leg: with skipDuplicates=true the claim says the timeout path stays correct. */
  public void testControlSkipDuplicatesUnderTimeout() throws Exception {
    Analyzer a = new StandardAnalyzer();
    try (Directory dir = index(300, a);
        DirectoryReader r = DirectoryReader.open(dir)) {
      List<String> expected = render(new SuggestIndexSearcher(r).suggest(query(a), 5, true));
      IndexSearcher s = new IndexSearcher(r);
      s.setTimeout(() -> false);
      assertEquals(expected, render(s.search(query(a), manager(5, true))));
    }
    a.close();
  }

  /** BulkScorer#score(collector, acceptDocs, min, max): collect only docs in [min, max). */
  public void testBulkScorerCollectsOnlyInsideWindow() throws Exception {
    Analyzer a = new StandardAnalyzer();
    try (Directory dir = index(200, a);
        DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = new IndexSearcher(r);
      Query q = s.rewrite(query(a));
      Weight w = s.createWeight(q, ScoreMode.COMPLETE, 1f);
      LeafReaderContext ctx = r.leaves().get(0);
      BulkScorer bs = w.bulkScorer(ctx);
      assertNotNull(bs);
      final List<Integer> seen = new ArrayList<>();
      TopSuggestDocsCollector c =
          new TopSuggestDocsCollector(5, false) {
            @Override
            public void collect(int docID, CharSequence key, CharSequence context, float score)
                throws IOException {
              seen.add(docID);
              super.collect(docID, key, context, score);
            }
          };
      int min = 0;
      int max = 10;
      try {
        bs.score(c.getLeafCollector(ctx), null, min, max);
      } catch (org.apache.lucene.search.CollectionTerminatedException e) {
        // allowed by the collector contract
      }
      List<Integer> outside = new ArrayList<>();
      for (int d : seen) {
        if (d < min || d >= max) {
          outside.add(d);
        }
      }
      assertEquals("docs collected outside [" + min + ", " + max + ")", List.of(), outside);
    }
    a.close();
  }
}
