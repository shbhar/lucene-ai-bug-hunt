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
import java.util.List;
import org.apache.lucene.codecs.hnsw.DefaultFlatVectorScorer;
import org.apache.lucene.index.FloatVectorValues;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestInitializedHnswGraphBuilderNullNodes extends LuceneTestCase {

  // Expected: initializedNodes "can be null if not tracking", so node 1 is added; actual: NPE.
  public void testAddNodeWithNullInitializedNodes() throws IOException {
    RandomVectorScorerSupplier scorer =
        DefaultFlatVectorScorer.INSTANCE.getRandomVectorScorerSupplier(
            VectorSimilarityFunction.EUCLIDEAN,
            FloatVectorValues.fromFloats(List.of(new float[] {1, 0}, new float[] {0, 1}), 2));
    OnHeapHnswGraph graph = HnswGraphBuilder.create(scorer, 16, 100, 42L).build(1);
    InitializedHnswGraphBuilder builder =
        InitializedHnswGraphBuilder.fromGraph(scorer, 100, 42L, graph, new int[] {0}, null, 2);
    builder.addGraphNode(1);
    assertEquals(2, builder.getGraph().size());
  }
}
