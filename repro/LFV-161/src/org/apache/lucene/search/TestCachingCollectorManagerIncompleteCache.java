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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * CachingCollectorManager reports isCached() == true, but the cache holds only the hits that the
 * wrapped first-pass collector let through, so replay(second) differs from search(query, second),
 * which is what the CachingCollectorManager class javadoc offers as the fallback when the cache
 * overflowed. The wrapped collector loses hits for the cache by:
 *
 * <ul>
 *   <li>pruning low-scoring hits (TOP_SCORES): with cacheScores=false the cache reports the
 *       wrapped TOP_SCORES score mode; with cacheScores=true it reports COMPLETE but still forwards
 *       the wrapped collector's setMinCompetitiveScore calls to the scorer;
 *   <li>terminating a leaf early (CollectionTerminatedException from LeafCollector#collect);
 *   <li>skipping a leaf (CollectionTerminatedException from Collector#getLeafCollector).
 * </ul>
 *
 * Public API only. One segment, sequential searcher.
 */
public class TestCachingCollectorManagerIncompleteCache extends LuceneTestCase {

  private static final int NUM_DOCS = 5000;

  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;
  private final Query query = new TermQuery(new Term("f", "a"));

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (int i = 0; i < NUM_DOCS; i++) {
        Document doc = new Document();
        doc.add(new StringField("f", "a", Field.Store.NO));
        w.addDocument(doc);
      }
      w.forceMerge(1);
    }
    reader = DirectoryReader.open(dir);
    searcher = new IndexSearcher(reader); // no executor: one slice, sequential
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  /** Replays the cache into a hit counter and compares with running the counter directly. */
  private void assertReplayCountsEveryHit(CollectorManager<?, ?> firstPass, boolean cacheScores)
      throws Exception {
    CachingCollectorManager<?, ?> caching =
        new CachingCollectorManager<>(firstPass, cacheScores, null, 2 * NUM_DOCS);
    searcher.search(query, caching);
    assertTrue("cache must not overflow: budget is twice the hit count", caching.isCached());
    int expected = searcher.search(query, new TotalHitCountCollectorManager(searcher.getSlices()));
    assertEquals(NUM_DOCS, expected);
    int replayed = caching.replay(new TotalHitCountCollectorManager(searcher.getSlices()));
    assertEquals(
        "replay into a hit counter (cacheScores=" + cacheScores + ") vs search(query, counter)",
        expected,
        replayed);
  }

  /** Control: a first pass that needs every hit (COMPLETE) is cached completely. */
  public void testControlCompleteFirstPass() throws Exception {
    assertReplayCountsEveryHit(new TopScoreDocCollectorManager(10, Integer.MAX_VALUE), false);
  }

  /** The default IndexSearcher#search(Query, int) settings: top 10, hits counted up to 1000. */
  public void testTopScoresFirstPassNoScoreCache() throws Exception {
    assertReplayCountsEveryHit(new TopScoreDocCollectorManager(10, 1000), false);
  }

  /** Same, caching scores: CachingCollector asks for COMPLETE, yet hits are still pruned. */
  public void testTopScoresFirstPassScoreCache() throws Exception {
    assertReplayCountsEveryHit(new TopScoreDocCollectorManager(10, 1000), true);
  }

  /** A first pass that terminates each leaf early (index-order sort, hit count threshold 1). */
  public void testEarlyTerminatingFirstPass() throws Exception {
    assertReplayCountsEveryHit(new TopFieldCollectorManager(Sort.INDEXORDER, 1, 1), false);
  }

  /** A first pass that skips leaves: the hit counter answers from Weight#count. */
  public void testLeafSkippingFirstPass() throws Exception {
    assertReplayCountsEveryHit(new TotalHitCountCollectorManager(searcher.getSlices()), false);
  }
}
