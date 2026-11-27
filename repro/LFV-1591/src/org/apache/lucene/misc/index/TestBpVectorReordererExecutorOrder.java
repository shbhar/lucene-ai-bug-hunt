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
package org.apache.lucene.misc.index;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import org.apache.lucene.index.FloatVectorValues;
import org.apache.lucene.index.Sorter;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.TaskExecutor;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestBpVectorReordererExecutorOrder extends LuceneTestCase {

  // expected: the order computed with an executor is the one computed without; actual: some differ
  public void testExecutorDoesNotChangeOrder() throws Exception {
    int size = 40_000;
    List<float[]> vectors = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      vectors.add(new float[] {(i + 4 * (i % 2)) * 1e-4f});
    }
    FloatVectorValues values = FloatVectorValues.fromFloats(vectors, 1);
    BpVectorReorderer reorderer = new BpVectorReorderer("field");
    Sorter.DocMap expected =
        reorderer.computeValueMap(values, VectorSimilarityFunction.EUCLIDEAN, null);
    ForkJoinPool pool = new ForkJoinPool(3);
    try {
      TaskExecutor executor = new TaskExecutor(pool);
      for (int call = 0; call < 100; call++) {
        Sorter.DocMap actual =
            pool.submit(
                    () ->
                        reorderer.computeValueMap(
                            values, VectorSimilarityFunction.EUCLIDEAN, executor))
                .get();
        for (int i = 0; i < size; i++) {
          assertEquals(
              "order with an executor, position " + i, expected.newToOld(i), actual.newToOld(i));
        }
      }
    } finally {
      pool.shutdown();
    }
  }
}
