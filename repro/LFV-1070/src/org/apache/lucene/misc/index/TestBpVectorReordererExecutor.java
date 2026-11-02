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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.lucene.index.FloatVectorValues;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.TaskExecutor;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestBpVectorReordererExecutor extends LuceneTestCase {

  public void testSameThreadExecutorKeepsWorkOnCaller() {
    Set<Thread> readers = ConcurrentHashMap.newKeySet();
    FloatVectorValues vectors =
        new FloatVectorValues() {
          @Override
          public float[] vectorValue(int ord) {
            readers.add(Thread.currentThread());
            return new float[] {ord % 61, ord % 7};
          }

          @Override
          public FloatVectorValues copy() {
            return this;
          }

          @Override
          public int dimension() {
            return 2;
          }

          @Override
          public int size() {
            return 4096;
          }
        };
    new BpVectorReorderer("v")
        .computeValueMap(vectors, VectorSimilarityFunction.EUCLIDEAN, new TaskExecutor(Runnable::run));
    // expected: only the caller reads vectors (same-thread executor); actual: ForkJoinPool.commonPool workers do too
    assertEquals(Set.of(Thread.currentThread()), readers);
  }
}
