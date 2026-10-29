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
package org.apache.lucene.tests.search;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.search.DocIdStream;
import org.apache.lucene.search.LeafCollector;
import org.apache.lucene.search.Scorable;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IOIntConsumer;

public class TestAssertingLeafCollectorForEachUpTo extends LuceneTestCase {

  /** The doc IDs 0 to 9, each consumed at most once. */
  private static class ZeroToTen extends DocIdStream {
    private int next;

    @Override
    public void forEach(int upTo, IOIntConsumer consumer) throws IOException {
      for (; next < Math.min(upTo, 10); next++) {
        consumer.accept(next);
      }
    }

    @Override
    public int count(int upTo) {
      int end = Math.max(next, Math.min(upTo, 10));
      int count = end - next;
      next = end;
      return count;
    }

    @Override
    public int intoArray(int upTo, int[] array) {
      int count = 0;
      while (count < array.length && next < Math.min(upTo, 10)) {
        array[count++] = next++;
      }
      return count;
    }

    @Override
    public boolean mayHaveRemaining() {
      return next < 10;
    }
  }

  public void testForEachUpTo() throws IOException {
    List<Integer> seen = new ArrayList<>();
    LeafCollector collector =
        new LeafCollector() {
          @Override
          public void setScorer(Scorable scorer) {}

          @Override
          public void collect(int doc) {}

          @Override
          public void collect(DocIdStream stream) throws IOException {
            stream.forEach(5, seen::add);
          }
        };
    // rarely() replays the stream as collect(int) calls instead of handing it over: retry
    while (seen.isEmpty()) {
      new AssertingLeafCollector(collector, 0, 10).collect(new ZeroToTen());
    }
    assertEquals("docs passed to forEach(upTo=5, ...)", List.of(0, 1, 2, 3, 4), seen);
  }
}
