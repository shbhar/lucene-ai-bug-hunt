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
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Release-compatible variant: the second-pass collector is a minimal SimpleCollector that stops
 * each leaf after its first hit with CollectionTerminatedException, as LeafCollector#collect
 * documents.
 */
@SuppressWarnings("deprecation")
public class TestCachingCollectorReplayTerminationLegacy extends LuceneTestCase {

  static final class FirstHitPerLeaf extends SimpleCollector {
    int hits;

    @Override
    public void collect(int doc) throws IOException {
      hits++;
      throw new CollectionTerminatedException();
    }

    @Override
    public ScoreMode scoreMode() {
      return ScoreMode.COMPLETE_NO_SCORES;
    }
  }

  private void run(boolean cacheScores) throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (int i = 0; i < 20; i++) {
          Document doc = new Document();
          doc.add(new StringField("f", "a", Field.Store.NO));
          w.addDocument(doc);
        }
        w.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Query query = new TermQuery(new Term("f", "a"));
        FirstHitPerLeaf direct = new FirstHitPerLeaf();
        searcher.search(query, direct); // IndexSearcher swallows the exception
        assertEquals(1, direct.hits);
        CachingCollector cc = CachingCollector.create(cacheScores, 1.0);
        searcher.search(query, cc);
        assertTrue(cc.isCached());
        FirstHitPerLeaf replayed = new FirstHitPerLeaf();
        cc.replay(replayed);
        assertEquals(direct.hits, replayed.hits);
      }
    }
  }

  public void testReplayNoScores() throws Exception {
    run(false);
  }

  public void testReplayWithScores() throws Exception {
    run(true);
  }
}
