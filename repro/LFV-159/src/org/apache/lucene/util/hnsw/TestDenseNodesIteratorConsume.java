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

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * HnswGraph.NodesIterator#consume: "Consume integers from the iterator and place them into the
 * dest array. @return The number of integers written to dest". ArrayNodesIterator and
 * CollectionNodesIterator advance past the consumed nodes; DenseNodesIterator (what
 * getNodesOnLevel(0) returns for on-heap and off-heap graphs) does not, so a drain loop never ends
 * and consume followed by nextInt returns the same nodes again. Latent: no in-tree caller consumes
 * a level-0 iterator today. Public API only.
 */
public class TestDenseNodesIteratorConsume extends LuceneTestCase {

  private static List<Integer> drain(HnswGraph.NodesIterator it, int bufferSize) {
    List<Integer> out = new ArrayList<>();
    int[] buf = new int[bufferSize];
    int guard = 0;
    while (it.hasNext()) {
      if (++guard > 100) {
        fail("consume() loop did not terminate; returned so far " + out.subList(0, Math.min(12, out.size())));
      }
      int n = it.consume(buf);
      for (int i = 0; i < n; i++) out.add(buf[i]);
    }
    return out;
  }

  public void testArrayNodesIteratorConsumeAdvances() {
    assertEquals(List.of(0, 1, 2, 3, 4), drain(new HnswGraph.ArrayNodesIterator(new int[] {0, 1, 2, 3, 4}), 3));
  }

  public void testDenseNodesIteratorConsumeAdvances() {
    assertEquals(List.of(0, 1, 2, 3, 4), drain(new HnswGraph.DenseNodesIterator(5), 3));
  }

  public void testConsumeThenNextInt() {
    HnswGraph.NodesIterator it = new HnswGraph.DenseNodesIterator(5);
    int[] buf = new int[3];
    assertEquals(3, it.consume(buf));
    assertEquals("next node after consuming 0, 1, 2", 3, it.nextInt());
  }
}
