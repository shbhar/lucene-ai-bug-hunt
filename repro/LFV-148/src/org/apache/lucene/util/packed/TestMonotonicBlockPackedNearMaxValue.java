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

import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestMonotonicBlockPackedNearMaxValue extends LuceneTestCase {

  public void testTwoValuesEndingAtLongMaxValue() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexOutput out = dir.createOutput("values", IOContext.DEFAULT)) {
        MonotonicBlockPackedWriter writer = new MonotonicBlockPackedWriter(out, 64);
        writer.add(1);
        writer.add(Long.MAX_VALUE);
        writer.finish();
      }
      try (IndexInput in = dir.openInput("values", IOContext.DEFAULT)) {
        MonotonicBlockPackedReader reader =
            MonotonicBlockPackedReader.of(in, PackedInts.VERSION_CURRENT, 64, 2);
        // expected Long.MAX_VALUE, actual Long.MIN_VALUE (min + (long) (avg * i) wraps in flush())
        assertEquals(Long.MAX_VALUE, reader.get(1));
      }
    }
  }
}
