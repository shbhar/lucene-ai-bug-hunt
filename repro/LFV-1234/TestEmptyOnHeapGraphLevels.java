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

import java.util.List;
import org.apache.lucene.codecs.hnsw.DefaultFlatVectorScorer;
import org.apache.lucene.index.FloatVectorValues;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestEmptyOnHeapGraphLevels extends LuceneTestCase {

  // Expected: every level an empty graph reports lists 0 nodes; actual: NullPointerException on
  // level 1.
  public void testEmptyGraphListsEveryLevel() throws Exception {
    RandomVectorScorerSupplier scorers =
        DefaultFlatVectorScorer.INSTANCE.getRandomVectorScorerSupplier(
            VectorSimilarityFunction.EUCLIDEAN, FloatVectorValues.fromFloats(List.of(), 2));
    OnHeapHnswGraph graph = HnswGraphBuilder.create(scorers, 8, 20, 1L).build(0);
    for (int level = 0; level < graph.numLevels(); level++) {
      assertEquals(0, graph.getNodesOnLevel(level).size());
    }
  }
}
