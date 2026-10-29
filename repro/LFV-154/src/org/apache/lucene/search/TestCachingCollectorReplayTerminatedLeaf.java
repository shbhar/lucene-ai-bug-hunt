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
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestCachingCollectorReplayTerminatedLeaf extends LuceneTestCase {

  @SuppressWarnings("deprecation")
  public void testReplayIntoCollectorThatTerminatesLeaf() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        w.addDocument(new Document());
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        CachingCollector cache = CachingCollector.create(false, 1.0);
        new IndexSearcher(reader).search(MatchAllDocsQuery.INSTANCE, cache);
        SimpleCollector terminating =
            new SimpleCollector() {
              @Override
              public void collect(int doc) {
                throw new CollectionTerminatedException();
              }

              @Override
              public ScoreMode scoreMode() {
                return ScoreMode.COMPLETE_NO_SCORES;
              }
            };
        try {
          cache.replay(terminating);
        } catch (CollectionTerminatedException e) {
          // Expected: replay ends the leaf, as IndexSearcher.search does; actual: the exception escapes.
          throw new AssertionError("CachingCollector.replay let " + e + " escape", e);
        }
      }
    }
  }
}
