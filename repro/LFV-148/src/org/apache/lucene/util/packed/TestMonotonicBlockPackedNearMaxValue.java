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
package org.apache.lucene.util.packed;

import java.io.IOException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * MonotonicBlockPackedWriter is documented as "a writer for large monotonically increasing sequences
 * of positive longs". Blocks whose values come close to Long.MAX_VALUE are read back wrong: the
 * writer's expected value min + (long) (avg * i) overflows, so the min adjustment is skipped and a
 * negative delta is encoded with too few bits.
 */
public class TestMonotonicBlockPackedNearMaxValue extends LuceneTestCase {

  private static long[] roundTrip(long[] values, int blockSize) throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexOutput out = dir.createOutput("values", IOContext.DEFAULT)) {
        MonotonicBlockPackedWriter writer = new MonotonicBlockPackedWriter(out, blockSize);
        for (long v : values) {
          writer.add(v);
        }
        writer.finish();
      }
      try (IndexInput in = dir.openInput("values", IOContext.DEFAULT)) {
        MonotonicBlockPackedReader reader =
            MonotonicBlockPackedReader.of(in, PackedInts.VERSION_CURRENT, blockSize, values.length);
        long[] read = new long[values.length];
        for (int i = 0; i < values.length; ++i) {
          read[i] = reader.get(i);
        }
        return read;
      }
    }
  }

  /** Two positive, strictly increasing values. */
  public void testTwoValuesEndingAtLongMaxValue() throws IOException {
    long[] values = {1L, Long.MAX_VALUE};
    assertArrayEquals(values, roundTrip(values, 64));
  }

  /** A full block of 64 strictly increasing positive values. */
  public void testStrictlyIncreasingBlockNearLongMaxValue() throws IOException {
    long[] values = new long[64];
    values[0] = 1L << 62;
    for (int i = 1; i < values.length; ++i) {
      values[i] = Long.MAX_VALUE - (values.length - 1 - i);
    }
    assertArrayEquals(values, roundTrip(values, 64));
  }
}
