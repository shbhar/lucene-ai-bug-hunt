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
package org.apache.lucene.util.quantization;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.apache.lucene.index.FloatVectorValues;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestAutoIntervalTiedNeighbourScores extends LuceneTestCase {

  public void testIdenticalVectorsDoNotResetQuantiles() throws Exception {
    Random r = new Random(0);
    List<float[]> data = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      float[] v = new float[8];
      for (int d = 0; d < 8; d++) {
        v[d] = r.nextFloat() * 2f - 1f;
      }
      data.add(v);
    }
    ScalarQuantizer expected =
        ScalarQuantizer.fromVectorsAutoInterval(
            FloatVectorValues.fromFloats(data, 8),
            VectorSimilarityFunction.EUCLIDEAN,
            20,
            (byte) 4);
    for (int i = 0; i < 11; i++) {
      float[] c = new float[8];
      Arrays.fill(c, 10f);
      data.add(c);
    }
    ScalarQuantizer actual =
        ScalarQuantizer.fromVectorsAutoInterval(
            FloatVectorValues.fromFloats(data, 8),
            VectorSimilarityFunction.EUCLIDEAN,
            31,
            (byte) 4);
    // expected: the same pair as above, (-0.96047044, 0.957112)
    // actual: the first pair of the candidate grid, (-0.9538363, 0.9618902)
    assertEquals(expected.toString(), actual.toString());
  }
}
