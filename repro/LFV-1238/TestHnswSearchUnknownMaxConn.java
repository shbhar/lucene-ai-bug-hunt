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
package org.apache.lucene.util.hnsw;

import java.io.IOException;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopKnnCollector;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestHnswSearchUnknownMaxConn extends LuceneTestCase {

  // Expected: the single node 0 is returned; actual: NegativeArraySizeException: -2.
  public void testSearchGraphWithUnknownMaxConn() throws IOException {
    HnswGraph graph =
        new HnswGraph() {
          @Override
          public void seek(int level, int target) {}

          @Override
          public int size() {
            return 1;
          }

          @Override
          public int nextNeighbor() {
            return DocIdSetIterator.NO_MORE_DOCS;
          }

          @Override
          public int numLevels() {
            return 1;
          }

          @Override
          public int maxConn() {
            return UNKNOWN_MAX_CONN;
          }

          @Override
          public int entryNode() {
            return 0;
          }

          @Override
          public NodesIterator getNodesOnLevel(int level) {
            throw new UnsupportedOperationException();
          }

          @Override
          public int neighborCount() {
            return 0;
          }
        };
    RandomVectorScorer scorer =
        new RandomVectorScorer() {
          @Override
          public int maxOrd() {
            return 1;
          }

          @Override
          public float score(int node) {
            return 1f;
          }
        };
    TopKnnCollector collector = new TopKnnCollector(1, Integer.MAX_VALUE);
    HnswGraphSearcher.search(scorer, collector, graph, null);
    TopDocs topDocs = collector.topDocs();
    assertEquals(1, topDocs.scoreDocs.length);
    assertEquals(0, topDocs.scoreDocs[0].doc);
  }
}
