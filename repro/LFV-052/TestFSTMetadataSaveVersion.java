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
import org.apache.lucene.util.IntsRef;
import org.apache.lucene.util.IntsRefBuilder;

/** FST#save must write the version of the FST being saved, not always VERSION_CURRENT. */
public class TestFSTMetadataSaveVersion extends LuceneTestCase {

  /**
   * An FST written by Lucene 8.11.4 (FST format version 7): INPUT_TYPE.BYTE2, PositiveIntOutputs,
   * KEYS[i] mapped to i + 1, saved with fst.save(meta, data). Lucene 8.x wrote BYTE2 labels
   * big-endian.
   */
  private static final byte[] V7_META = HexFormat.of().parseHex("3fd76c17034653540000000700013f40");

  private static final byte[] V7_DATA =
      HexFormat.of()
          .parseHex(
              "000264001f0163001d62000d74000f61000667000f6f0006019e8a1f2c67071e08e565121e07fc0019"
                  + "1706640010110563001005046200190b01610010050620");

  private static final String[] KEYS = {"ab", "ac", "ad", "b", "cat", "dog", "ü", "日本", "日本語"};

  private static FST<Long> load(byte[] meta, byte[] data) throws IOException {
    FST.FSTMetadata<Long> md =
        FST.readMetadata(new ByteArrayDataInput(meta), PositiveIntOutputs.getSingleton());
    return new FST<>(md, new ByteArrayDataInput(data));
  }

  private static byte[][] save(FST<Long> fst) throws IOException {
    ByteBuffersDataOutput meta = new ByteBuffersDataOutput();
    ByteBuffersDataOutput data = new ByteBuffersDataOutput();
    fst.save(meta, data);
    return new byte[][] {meta.toArrayCopy(), data.toArrayCopy()};
  }

  private static int keysFound(FST<Long> fst) throws IOException {
    IntsRefBuilder scratch = new IntsRefBuilder();
    int found = 0;
    for (int i = 0; i < KEYS.length; i++) {
      if (Long.valueOf(i + 1).equals(Util.get(fst, Util.toUTF16(KEYS[i], scratch)))) {
        found++;
      }
    }
    return found;
  }

  /** Control: the version 7 FST loads and every key is found. */
  public void testLoadPre90Byte2Fst() throws Exception {
    FST<Long> fst = load(V7_META, V7_DATA);
    assertEquals(7, fst.getMetadata().getVersion());
    assertEquals("keys found", KEYS.length, keysFound(fst));
  }

  /** Saving the loaded FST and loading the copy must keep every key. */
  public void testResavedPre90Byte2FstFindsAllKeys() throws Exception {
    FST<Long> original = load(V7_META, V7_DATA);
    assertEquals("keys found in the original", KEYS.length, keysFound(original));

    byte[][] saved = save(original);
    FST<Long> copy = load(saved[0], saved[1]);
    assertEquals(
        "keys found after save + reload (reloaded getVersion()="
            + copy.getMetadata().getVersion()
            + ")",
        KEYS.length,
        keysFound(copy));
  }

  /**
   * FSTMetadata#getVersion returns "the version constant of the binary format this FST was written
   * in". An FST built with setVersion(VERSION_90) must still report VERSION_90 after save +
   * readMetadata.
   */
  public void testSaveKeepsVersionOfVersion90Fst() throws Exception {
    FSTCompiler<Long> compiler =
        new FSTCompiler.Builder<>(FST.INPUT_TYPE.BYTE1, PositiveIntOutputs.getSingleton())
            .setVersion(FST.VERSION_90)
            .build();
    String keys = "abcdef";
    for (int i = 0; i < keys.length(); i++) {
      compiler.add(new IntsRef(new int[] {keys.charAt(i)}, 0, 1), (long) (i + 1));
    }
    FST<Long> fst = FST.fromFSTReader(compiler.compile(), compiler.getFSTReader());
    assertEquals("in-memory version", FST.VERSION_90, fst.getMetadata().getVersion());

    byte[][] saved = save(fst);
    FST.FSTMetadata<Long> reloaded =
        FST.readMetadata(new ByteArrayDataInput(saved[0]), PositiveIntOutputs.getSingleton());
    assertEquals("version after save + readMetadata", FST.VERSION_90, reloaded.getVersion());
  }
}
