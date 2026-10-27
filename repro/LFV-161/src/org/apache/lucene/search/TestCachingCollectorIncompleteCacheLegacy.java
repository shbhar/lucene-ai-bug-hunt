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

/** Release-compatible variant: CachingCollector + the deprecated search(Query, Collector). */
@SuppressWarnings("deprecation")
public class TestCachingCollectorIncompleteCacheLegacy extends LuceneTestCase {

  private static final int NUM_DOCS = 5000;

  private void run(Collector firstPass, boolean cacheScores) throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (int i = 0; i < NUM_DOCS; i++) {
          Document doc = new Document();
          doc.add(new StringField("f", "a", Field.Store.NO));
          w.addDocument(doc);
        }
        w.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Query query = new TermQuery(new Term("f", "a"));
        CachingCollector cc = CachingCollector.create(firstPass, cacheScores, 2 * NUM_DOCS);
        searcher.search(query, cc);
        assertTrue(cc.isCached());
        TotalHitCountCollector replayed = new TotalHitCountCollector();
        cc.replay(replayed);
        assertEquals("replay (cacheScores=" + cacheScores + ")", NUM_DOCS, replayed.getTotalHits());
      }
    }
  }

  public void testControlCompleteFirstPass() throws Exception {
    run(new TopScoreDocCollectorManager(10, Integer.MAX_VALUE).newCollector(), false);
  }

  public void testTopScoresFirstPassNoScoreCache() throws Exception {
    run(new TopScoreDocCollectorManager(10, 1000).newCollector(), false);
  }

  public void testTopScoresFirstPassScoreCache() throws Exception {
    run(new TopScoreDocCollectorManager(10, 1000).newCollector(), true);
  }

  public void testEarlyTerminatingFirstPass() throws Exception {
    run(new TopFieldCollectorManager(Sort.INDEXORDER, 1, 1).newCollector(), false);
  }

  public void testLeafSkippingFirstPass() throws Exception {
    run(new TotalHitCountCollector(), false);
  }
}
