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
package org.apache.lucene.queries.intervals;

import java.util.function.Predicate;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LRUQueryCache;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryCachingPolicy;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.analysis.SimplePayloadFilter;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * Intervals.term(term, payloadFilter) builds a source whose equals/hashCode only look at the term,
 * not at the payload filter. Sources that select different positions therefore compare equal, and
 * every place that de-duplicates by equality (Intervals.or, ordered/unordered repeat detection,
 * BooleanQuery rewrite) silently changes the query.
 *
 * <p>Index: the analyzer stores the payload "pos: N" at each position N. doc "first" = "a x" (a at
 * position 0), doc "second" = "x a" (a at position 1), doc "both" = "a a".
 */
public class TestPayloadFilterIgnoredByEquals extends LuceneTestCase {

  private static Predicate<BytesRef> payloadIs(String expected) {
    return payload -> payload != null && payload.utf8ToString().equals(expected);
  }

  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    Analyzer analyzer =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer tok = new MockTokenizer(MockTokenizer.WHITESPACE, true);
            return new TokenStreamComponents(tok, new SimplePayloadFilter(tok));
          }
        };
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
      addDoc(w, "first", "a x");
      addDoc(w, "second", "x a");
      addDoc(w, "both", "a a");
    }
    reader = DirectoryReader.open(dir);
    // no query cache, so the failures below come from the query objects alone
    searcher = new IndexSearcher(reader);
    searcher.setQueryCache(null);
  }

  private static void addDoc(IndexWriter w, String id, String text) throws Exception {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.YES));
    doc.add(new TextField("f", text, Field.Store.NO));
    w.addDocument(doc);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  private String hits(Query q) throws Exception {
    StoredFields sf = searcher.storedFields();
    java.util.TreeSet<String> ids = new java.util.TreeSet<>();
    for (ScoreDoc sd : searcher.search(q, 10).scoreDocs) {
      ids.add(sf.document(sd.doc).get("id"));
    }
    return ids.toString();
  }

  /** Sanity check: each filtered source on its own selects the expected documents. */
  public void testEachFilterAlone() throws Exception {
    assertEquals("[both, first]", hits(new IntervalQuery("f", Intervals.term("a", payloadIs("pos: 0")))));
    assertEquals("[both, second]", hits(new IntervalQuery("f", Intervals.term("a", payloadIs("pos: 1")))));
  }

  /** The two sources match different positions but compare equal. */
  public void testSourcesWithDifferentFiltersAreEqual() {
    IntervalsSource atPos0 = Intervals.term("a", payloadIs("pos: 0"));
    IntervalsSource atPos1 = Intervals.term("a", payloadIs("pos: 1"));
    assertFalse("sources selecting different positions must not be equal", atPos0.equals(atPos1));
  }

  /** Intervals.or de-duplicates its sub-sources with a HashSet and keeps only one of them. */
  public void testDisjunctionDropsASource() throws Exception {
    Query q =
        new IntervalQuery(
            "f",
            Intervals.or(Intervals.term("a", payloadIs("pos: 0")), Intervals.term("a", payloadIs("pos: 1"))));
    assertEquals("[both, first, second]", hits(q));
  }

  /** ordered(a@0, a@1) is turned into "a@0 repeated twice", which no document contains. */
  public void testOrderedTreatsDifferentFiltersAsRepeat() throws Exception {
    Query q =
        new IntervalQuery(
            "f",
            Intervals.ordered(
                Intervals.term("a", payloadIs("pos: 0")), Intervals.term("a", payloadIs("pos: 1"))));
    assertEquals("[both]", hits(q));
  }

  /**
   * BooleanQuery keeps FILTER clauses in a set, so the second filter is dropped and documents that
   * fail it are returned.
   */
  public void testBooleanTwoFiltersOverMatch() throws Exception {
    Query q =
        new BooleanQuery.Builder()
            .add(new IntervalQuery("f", Intervals.term("a", payloadIs("pos: 0"))), Occur.FILTER)
            .add(new IntervalQuery("f", Intervals.term("a", payloadIs("pos: 1"))), Occur.FILTER)
            .build();
    // only "both" has a at position 0 and a at position 1
    assertEquals("[both]", hits(q));
  }

  /**
   * A query cache keys entries by Query#equals/hashCode, so the cached result of one payload filter
   * is served for the other. (Always-cache policy and no minimum segment size, to make the cache
   * engage on this small index; the default cache does the same once its policy decides to cache.)
   */
  public void testQueryCacheServesTheOtherFiltersResult() throws Exception {
    IndexSearcher cached = new IndexSearcher(reader);
    cached.setQueryCache(new LRUQueryCache(100, 1 << 20, ctx -> true, Float.POSITIVE_INFINITY));
    cached.setQueryCachingPolicy(
        new QueryCachingPolicy() {
          @Override
          public void onUse(Query query) {}

          @Override
          public boolean shouldCache(Query query) {
            return true;
          }
        });
    Query atPos0 = new IntervalQuery("f", Intervals.term("a", payloadIs("pos: 0")));
    Query atPos1 = new IntervalQuery("f", Intervals.term("a", payloadIs("pos: 1")));
    // without a cache: a at position 0 in "first" and "both"; a at position 1 in "second" and "both"
    assertEquals(2, searcher.count(atPos0));
    assertEquals(2, searcher.count(atPos1));
    String uncached = hits(atPos1);
    assertEquals(2, cached.count(atPos0)); // fills the cache
    IndexSearcher plain = searcher;
    searcher = cached;
    try {
      assertEquals("cached searcher must return the same hits for the second filter", uncached, hits(new org.apache.lucene.search.ConstantScoreQuery(atPos1)));
    } finally {
      searcher = plain;
    }
  }

  /** BooleanQuery.rewrite sees the MUST clause again in MUST_NOT and returns MatchNoDocsQuery. */
  public void testBooleanMustNotOfOtherFilterMatchesNothing() throws Exception {
    Query q =
        new BooleanQuery.Builder()
            .add(new IntervalQuery("f", Intervals.term("a", payloadIs("pos: 0"))), Occur.MUST)
            .add(new IntervalQuery("f", Intervals.term("a", payloadIs("pos: 1"))), Occur.MUST_NOT)
            .build();
    // "first" has a at position 0 and no a at position 1
    assertEquals("[first]", hits(q));
  }
}
