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
package org.apache.lucene.util.fst;

import java.io.IOException;
import java.util.HexFormat;
import org.apache.lucene.store.ByteArrayDataInput;
import org.apache.lucene.store.ByteBuffersDataOutput;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IntsRefBuilder;

public class TestResavePre90Byte2Fst extends LuceneTestCase {

  // Saved by Lucene 8.11.4 (FST version 7): INPUT_TYPE.BYTE2, PositiveIntOutputs, "a" -> 1.
  private static final byte[] META = HexFormat.of().parseHex("3fd76c17034653540000000700010405");
  private static final byte[] DATA = HexFormat.of().parseHex("000161001f");

  private static FST<Long> load(byte[] meta, byte[] data) throws IOException {
    return new FST<>(
        FST.readMetadata(new ByteArrayDataInput(meta), PositiveIntOutputs.getSingleton()),
        new ByteArrayDataInput(data));
  }

  public void testResaveKeepsLookups() throws Exception {
    ByteBuffersDataOutput meta = new ByteBuffersDataOutput();
    ByteBuffersDataOutput data = new ByteBuffersDataOutput();
    load(META, DATA).save(meta, data);
    FST<Long> copy = load(meta.toArrayCopy(), data.toArrayCopy());
    // Expected 1 as in the loaded FST; actual null (copy is labelled VERSION_CURRENT: labels swap).
    assertEquals(Long.valueOf(1), Util.get(copy, Util.toUTF16("a", new IntsRefBuilder())));
  }
}
