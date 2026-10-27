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
package org.apache.lucene.search;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * Resolution probe for the fact checks of LFV-106: the similarity query as a non-scoring clause
 * (FILTER or MUST_NOT) of a top-level scoring BooleanQuery. The clause Weight is created with
 * COMPLETE_NO_SCORES, so the query cache wraps it although the parent search scores. As a MUST_NOT
 * clause, a cached set that misses documents makes the parent match too many.
 */
public class TestVectorSimilarityQueryScoringParent extends LuceneTestCase {

  private static final int NUM_DOCS = 100;

  private static final QueryCachingPolicy ALWAYS_CACHE =
      new QueryCachingPolicy() {
        @Override
        public void onUse(Query query) {}

        @Override
        public boolean shouldCache(Query query) {
          return true;
        }
      };

  private static QueryCache newCache() {
    return new LRUQueryCache(1000, 1 << 24, _ -> true, Float.POSITIVE_INFINITY);
  }

  private static IndexSearcher searcher(DirectoryReader reader, QueryCache cache) {
    IndexSearcher searcher = new IndexSearcher(reader);
    searcher.setQueryCache(cache);
    searcher.setQueryCachingPolicy(ALWAYS_CACHE);
    return searcher;
  }

  /** A scoring search: TopDocs from search(Query, int), not a count. */
  private static int hits(IndexSearcher searcher, Query query) throws IOException {
    return searcher.search(query, 100_000).scoreDocs.length;
  }

  private static IndexWriter newWriter(Directory dir) throws IOException {
    IndexWriter w =
        new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()));
    for (int i = 0; i < NUM_DOCS; i++) {
      Document doc = new Document();
      doc.add(new StringField("id", Integer.toString(i), Field.Store.NO));
      doc.add(new StringField("all", "yes", Field.Store.NO));
      doc.add(new KnnFloatVectorField("v", new float[] {i, 1}, VectorSimilarityFunction.EUCLIDEAN));
      w.addDocument(doc);
    }
    w.forceMerge(1);
    w.commit();
    return w;
  }

  /** Matches every document (every similarity is above 0). */
  private static Query allVectors(float decay) {
    return new FloatVectorSimilarityQuery.Adaptive("v", new float[] {0, 1}, 0f, decay, null);
  }

  /** A scoring TermQuery that matches every document, with the similarity query as a clause. */
  private static Query scoringParent(Query vectors, BooleanClause.Occur occur) {
    return new BooleanQuery.Builder()
        .add(new TermQuery(new Term("all", "yes")), BooleanClause.Occur.MUST)
        .add(vectors, occur)
        .build();
  }

  /** The newer reader (doc 0 deleted) searches first and fills the cache; then the older reader. */
  private int olderReaderHits(Query query, int expectedNewer) throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter w = newWriter(dir);
        DirectoryReader older = DirectoryReader.open(w)) {
      w.deleteDocuments(new Term("id", "0"));
      try (DirectoryReader newer = DirectoryReader.openIfChanged(older, w)) {
        QueryCache cache = newCache();
        assertEquals(expectedNewer, hits(searcher(newer, cache), query));
        return hits(searcher(older, cache), query);
      }
    }
  }

  /** FILTER clause of a scoring query: the older reader must keep doc 0 (100 hits). */
  public void testFilterClauseOfScoringQuery() throws IOException {
    Query query = scoringParent(allVectors(1f), BooleanClause.Occur.FILTER);
    assertEquals("older reader", NUM_DOCS, olderReaderHits(query, NUM_DOCS - 1));
  }

  /** MUST_NOT clause: every document matches the vectors, so nothing may be returned. */
  public void testMustNotClauseOlderReader() throws IOException {
    Query query = scoringParent(allVectors(1f), BooleanClause.Occur.MUST_NOT);
    assertEquals("older reader", 0, olderReaderHits(query, 0));
  }

  /** MUST_NOT clause after a timed-out search filled the cache with its (empty) result. */
  public void testMustNotClauseAfterTimedOutSearch() throws IOException {
    Query query = scoringParent(allVectors(0.5f), BooleanClause.Occur.MUST_NOT);
    try (Directory dir = newDirectory();
        IndexWriter w = newWriter(dir);
        DirectoryReader reader = DirectoryReader.open(w)) {
      assertEquals("no cache, no timeout", 0, hits(searcher(reader, null), query));
      QueryCache cache = newCache();
      IndexSearcher timedOut = searcher(reader, cache);
      timedOut.setTimeout(() -> true);
      timedOut.search(query, NUM_DOCS); // a partial result is expected for this search only
      assertEquals("later search, no timeout", 0, hits(searcher(reader, cache), query));
    }
  }

  /** Control: the uncached scoring search gives the right answers in the same sequences. */
  public void testUncachedControl() throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter w = newWriter(dir);
        DirectoryReader older = DirectoryReader.open(w)) {
      w.deleteDocuments(new Term("id", "0"));
      try (DirectoryReader newer = DirectoryReader.openIfChanged(older, w)) {
        Query filter = scoringParent(allVectors(1f), BooleanClause.Occur.FILTER);
        Query mustNot = scoringParent(allVectors(1f), BooleanClause.Occur.MUST_NOT);
        assertEquals(NUM_DOCS - 1, hits(searcher(newer, null), filter));
        assertEquals(NUM_DOCS, hits(searcher(older, null), filter));
        assertEquals(0, hits(searcher(newer, null), mustNot));
        assertEquals(0, hits(searcher(older, null), mustNot));
      }
    }
  }
}
