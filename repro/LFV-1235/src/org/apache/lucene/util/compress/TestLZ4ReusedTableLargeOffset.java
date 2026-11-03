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
package org.apache.lucene.util.compress;

import java.util.Random;
import org.apache.lucene.store.ByteBuffersDataOutput;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestLZ4ReusedTableLargeOffset extends LuceneTestCase {

  public void testReuseAfterLongInput() throws Exception {
    byte[] data = new byte[1_100_000_000];
    byte[] tail = new byte[1 << 20];
    new Random(0).nextBytes(tail);
    int off = data.length - tail.length;
    System.arraycopy(tail, 0, data, off, tail.length);
    LZ4.FastCompressionHashTable ht = new LZ4.FastCompressionHashTable();
    LZ4.compress(data, 0, data.length, new ByteBuffersDataOutput(), ht);
    // expected: the reused table compresses the slice; actual: ArrayIndexOutOfBoundsException
    LZ4.compress(data, off, tail.length, new ByteBuffersDataOutput(), ht);
  }
}
