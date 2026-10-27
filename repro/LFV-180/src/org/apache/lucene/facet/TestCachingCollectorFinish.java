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
package org.apache.lucene.facet;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.CachingCollectorManager;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopScoreDocCollectorManager;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * CachingCollector never calls LeafCollector#finish where it should:
 *
 * <ol>
 *   <li>on the wrapped (first-pass) collector: NoScoreCachingLeafCollector#finish overrides
 *       FilterLeafCollector#finish without calling in.finish();
 *   <li>on the second-pass collector when replaying cached scores: ScoreCachingCollector#collect
 *       overrides NoScoreCachingCollector#collect and drops its collector.finish() call.
 * </ol>
 *
 * FacetsCollector records each leaf's MatchingDocs in LeafCollector#finish, so it ends up with no
 * hits. Public API only. One segment, one slice, sequential searcher.
 */
public class TestCachingCollectorFinish extends LuceneTestCase {

  private static final int NUM_DOCS = 20;

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

  private static int totalHits(FacetsCollector fc) {
    int total = 0;
    for (FacetsCollector.MatchingDocs md : fc.getMatchingDocs()) {
      total += md.totalHits();
    }
    return total;
  }

  /** Control: without the cache, FacetsCollectorManager sees every hit. */
  public void testControlFacetsWithoutCache() throws Exception {
    assertEquals(NUM_DOCS, totalHits(searcher.search(query, new FacetsCollectorManager())));
  }

  /** Control: replaying a cache WITHOUT scores into FacetsCollectorManager works. */
  public void testControlNoScoreReplayIntoFacets() throws Exception {
    CachingCollectorManager<?, ?> caching =
        new CachingCollectorManager<>(
            new TopScoreDocCollectorManager(10, Integer.MAX_VALUE), false, null, 1000);
    searcher.search(query, caching);
    assertTrue(caching.isCached());
    assertEquals(NUM_DOCS, totalHits(caching.replay(new FacetsCollectorManager())));
  }

  /** Manifestation 1: the wrapped first-pass FacetsCollector gets no finish() call. */
  public void testFacetsAsCachedFirstPass() throws Exception {
    CachingCollectorManager<FacetsCollector, FacetsCollector> caching =
        new CachingCollectorManager<>(new FacetsCollectorManager(), false, null, 1000);
    FacetsCollector firstPass = searcher.search(query, caching);
    assertTrue(caching.isCached());
    assertEquals(
        "first-pass result of a cached FacetsCollectorManager",
        NUM_DOCS,
        totalHits(firstPass));
  }

  /** Manifestation 2: replaying a cache WITH scores never calls finish() on the second pass. */
  public void testScoreCachingReplayIntoFacets() throws Exception {
    CachingCollectorManager<?, ?> caching =
        new CachingCollectorManager<>(
            new TopScoreDocCollectorManager(10, Integer.MAX_VALUE), true, null, 1000);
    searcher.search(query, caching);
    assertTrue(caching.isCached());
    FacetsCollector replayed = caching.replay(new FacetsCollectorManager(true));
    assertEquals("replay(cacheScores=true) into FacetsCollectorManager", NUM_DOCS, totalHits(replayed));
  }
}
