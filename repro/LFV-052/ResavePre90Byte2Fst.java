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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import org.apache.lucene.util.IntsRefBuilder;
import org.apache.lucene.util.fst.FST;
import org.apache.lucene.util.fst.PositiveIntOutputs;
import org.apache.lucene.util.fst.Util;

/**
 * Same scenario as TestFSTMetadataSaveVersion#testResavedPre90Byte2FstFindsAllKeys, written only
 * against FST.read(Path) / FST.save(Path) so that one source runs on Lucene 8.11 through main.
 * Exit 0: the re-saved copy finds every key. Exit 1: it does not.
 */
public class ResavePre90Byte2Fst {
  // Same Lucene 8.11.4 (FST format 7) BYTE2 fixture as TestFSTMetadataSaveVersion.
  static final byte[] V7_META = HexFormat.of().parseHex("3fd76c17034653540000000700013f40");
  static final byte[] V7_DATA =
      HexFormat.of()
          .parseHex(
              "000264001f0163001d62000d74000f61000667000f6f0006019e8a1f2c67071e08e565121e07fc0019"
                  + "1706640010110563001005046200190b01610010050620");
  static final String[] KEYS = {
    "ab", "ac", "ad", "b", "cat", "dog", "ü", "日本", "日本語"
  };

  static int keysFound(FST<Long> fst) throws Exception {
    IntsRefBuilder scratch = new IntsRefBuilder();
    int found = 0;
    for (int i = 0; i < KEYS.length; i++) {
      if (Long.valueOf(i + 1).equals(Util.get(fst, Util.toUTF16(KEYS[i], scratch)))) {
        found++;
      }
    }
    return found;
  }

  /** CodecUtil header: magic (4) | vint length (1) | "FST" (3) | big-endian version (4). */
  static int headerVersion(byte[] file) {
    return ((file[8] & 0xFF) << 24)
        | ((file[9] & 0xFF) << 16)
        | ((file[10] & 0xFF) << 8)
        | (file[11] & 0xFF);
  }

  public static void main(String[] args) throws Exception {
    Path dir = Files.createTempDirectory("fst-resave");
    // FST.save(Path) writes the metadata followed by the FST bytes into one file.
    Path original = dir.resolve("original.fst");
    byte[] file = new byte[V7_META.length + V7_DATA.length];
    System.arraycopy(V7_META, 0, file, 0, V7_META.length);
    System.arraycopy(V7_DATA, 0, file, V7_META.length, V7_DATA.length);
    Files.write(original, file);

    FST<Long> loaded = FST.read(original, PositiveIntOutputs.getSingleton());
    int before = keysFound(loaded);

    Path copyPath = dir.resolve("copy.fst");
    loaded.save(copyPath);
    byte[] copyBytes = Files.readAllBytes(copyPath);
    FST<Long> copy = FST.read(copyPath, PositiveIntOutputs.getSingleton());
    int after = keysFound(copy);

    System.out.println("lucene " + org.apache.lucene.util.Version.LATEST);
    System.out.println(
        "original: header version "
            + headerVersion(file)
            + ", keys found "
            + before
            + "/"
            + KEYS.length);
    System.out.println(
        "re-saved: header version "
            + headerVersion(copyBytes)
            + ", keys found "
            + after
            + "/"
            + KEYS.length
            + ", FST bytes unchanged: "
            + java.util.Arrays.equals(
                java.util.Arrays.copyOfRange(file, V7_META.length, file.length),
                java.util.Arrays.copyOfRange(copyBytes, copyBytes.length - V7_DATA.length, copyBytes.length)));
    for (Path p : new Path[] {original, copyPath, dir}) {
      Files.deleteIfExists(p);
    }
    if (before != KEYS.length) {
      System.out.println("SETUP: the original FST does not load correctly on this version");
      System.exit(2);
    }
    System.exit(after == KEYS.length ? 0 : 1);
  }
}
