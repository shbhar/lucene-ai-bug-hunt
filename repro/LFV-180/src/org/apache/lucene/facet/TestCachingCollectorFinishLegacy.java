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
import org.apache.lucene.search.CachingCollector;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Release-compatible variant (CachingCollector + the deprecated IndexSearcher#search(Query,
 * Collector), present in 9.x and 10.x): CachingCollector does not call LeafCollector#finish on the
 * wrapped collector, nor on the replayed collector when scores are cached.
 */
@SuppressWarnings("deprecation")
public class TestCachingCollectorFinishLegacy extends LuceneTestCase {

  private static final int NUM_DOCS = 20;

  private static int totalHits(FacetsCollector fc) throws Exception {
    int total = 0;
    for (Object md : fc.getMatchingDocs()) {
      // MatchingDocs.totalHits is a public field in 9.x and a record component in 10.x
      try {
        total += (Integer) md.getClass().getMethod("totalHits").invoke(md);
      } catch (NoSuchMethodException e) {
        total += md.getClass().getField("totalHits").getInt(md);
      }
    }
    return total;
  }

  private void run(boolean firstPassFacets, boolean cacheScores) throws Exception {
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
        if (firstPassFacets) {
          FacetsCollector first = new FacetsCollector();
          CachingCollector cc = CachingCollector.create(first, cacheScores, 1000);
          searcher.search(query, cc);
          assertTrue(cc.isCached());
          assertEquals("first pass wrapped in CachingCollector", NUM_DOCS, totalHits(first));
        } else {
          CachingCollector cc = CachingCollector.create(cacheScores, 1.0);
          searcher.search(query, cc);
          assertTrue(cc.isCached());
          FacetsCollector second = new FacetsCollector(cacheScores);
          cc.replay(second);
          assertEquals("replay(cacheScores=" + cacheScores + ")", NUM_DOCS, totalHits(second));
        }
      }
    }
  }

  public void testControlNoScoreReplayIntoFacets() throws Exception {
    run(false, false);
  }

  public void testFacetsAsCachedFirstPass() throws Exception {
    run(true, false);
  }

  public void testScoreCachingReplayIntoFacets() throws Exception {
    run(false, true);
  }
}
