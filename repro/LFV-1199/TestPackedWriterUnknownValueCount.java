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
import org.apache.lucene.store.ByteArrayDataInput;
import org.apache.lucene.store.ByteBuffersDataOutput;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestPackedWriterUnknownValueCount extends LuceneTestCase {

  public void testUnknownValueCountWithDefaultBufferSize() throws IOException {
    // Expected: valueCount -1 (documented "unknown count") writes and reads back 5; actual: add
    // throws AIOOBE.
    ByteBuffersDataOutput out = new ByteBuffersDataOutput();
    PackedInts.Writer writer =
        PackedInts.getWriterNoHeader(
            out, PackedInts.Format.PACKED, -1, 7, PackedInts.DEFAULT_BUFFER_SIZE);
    writer.add(5);
    writer.finish();
    PackedInts.ReaderIterator it =
        PackedInts.getReaderIteratorNoHeader(
            new ByteArrayDataInput(out.toArrayCopy()),
            PackedInts.Format.PACKED,
            PackedInts.VERSION_CURRENT,
            1,
            7,
            1);
    assertEquals(5, it.next());
  }
}
