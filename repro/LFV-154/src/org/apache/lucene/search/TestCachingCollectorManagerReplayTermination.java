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
 * CachingCollector#replay lets a CollectionTerminatedException thrown by the second-pass collector
 * escape to the caller. LeafCollector#collect documents this exception as the way to stop
 * collecting the current leaf, and IndexSearcher swallows it; replay plays IndexSearcher's role for
 * the second pass but does not. Public API only. One segment, sequential searcher.
 */
public class TestCachingCollectorManagerReplayTermination extends LuceneTestCase {

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

  private void assertReplayEqualsSearch(boolean cacheScores) throws Exception {
    CachingCollectorManager<?, ?> caching =
        new CachingCollectorManager<>(
            new TopScoreDocCollectorManager(10, Integer.MAX_VALUE), cacheScores, null, 1000);
    searcher.search(query, caching);
    assertTrue(caching.isCached());
    // Second pass: the first document in index order. With a hit-count threshold of 1,
    // TopFieldCollector stops collecting a leaf (CollectionTerminatedException) once it is sure.
    TopFieldDocs expected = searcher.search(query, new TopFieldCollectorManager(Sort.INDEXORDER, 1, 1));
    assertEquals(1, expected.scoreDocs.length);
    TopFieldDocs replayed = caching.replay(new TopFieldCollectorManager(Sort.INDEXORDER, 1, 1));
    assertEquals(expected.scoreDocs[0].doc, replayed.scoreDocs[0].doc);
  }

  public void testReplayIntoEarlyTerminatingCollectorNoScores() throws Exception {
    assertReplayEqualsSearch(false);
  }

  public void testReplayIntoEarlyTerminatingCollectorWithScores() throws Exception {
    assertReplayEqualsSearch(true);
  }
}
