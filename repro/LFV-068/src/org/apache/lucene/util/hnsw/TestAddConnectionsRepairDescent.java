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

public class TestAddConnectionsRepairDescent extends LuceneTestCase {

  private static final int E = 0, P = 1, X = 2, D = 3;

  public void testRepairDescendsFromClosestUpperLevelNode() throws Exception {
    OnHeapHnswGraph graph = new OnHeapHnswGraph(16, 4);
    for (int node : new int[] {E, P, X, D}) {
      graph.addNode(0, node);
    }
    graph.addNode(1, E);
    graph.addNode(1, P);
    graph.trySetNewEntryNode(E, 1);
    graph.getNeighbors(1, E).addOutOfOrder(P, Float.NaN);
    graph.getNeighbors(0, X).addOutOfOrder(D, Float.NaN);
    List<float[]> vectorsOfEPX = List.of(new float[] {0}, new float[] {100}, new float[] {1});
    RandomVectorScorerSupplier scorers =
        DefaultFlatVectorScorer.INSTANCE.getRandomVectorScorerSupplier(
            VectorSimilarityFunction.EUCLIDEAN, FloatVectorValues.fromFloats(vectorsOfEPX, 1));
    OnHeapHnswGraph merged =
        InitializedHnswGraphBuilder.initGraph(graph, new int[] {E, P, X, -1}, 3, 100, scorers);
    merged.seek(0, X);
    // X lost its only neighbor D: expected a descent via E (closest to X), actual via P
    assertEquals("X's level-0 neighbor (E=0, P=1)", E, merged.nextNeighbor());
  }
}
