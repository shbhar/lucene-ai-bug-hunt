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
package org.apache.lucene.analysis.ko.dict;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ko.KoreanTokenizer;
import org.apache.lucene.analysis.morph.BinaryDictionary;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.util.IntsRef;

/**
 * Prints, for one-word dictionaries built with DictionaryBuilder, what each source context id turns
 * into: rejected at build time, or the id read back and the result of tokenizing the word. Public
 * API only. Usage: RightIdBandsProbe (no arguments); writes into a fresh temp directory.
 */
public class RightIdBandsProbe {

  private static Path file(Path dir, Class<?> clazz, String suffix) {
    return dir.resolve(clazz.getName().replace('.', '/') + suffix);
  }

  private static String run(Path root, int leftId, int rightId) throws Exception {
    Path dir = Files.createTempDirectory(root, "d");
    Files.write(
        dir.resolve("test.csv"),
        List.of("가나," + leftId + "," + rightId + ",100,NNG,*,F,가나,*,*,*,*"),
        StandardCharsets.UTF_8);
    Files.write(
        dir.resolve("char.def"),
        List.of("DEFAULT 0 1 0", "SPACE 0 1 0", "0x0020 SPACE"),
        StandardCharsets.UTF_8);
    Files.write(
        dir.resolve("unk.def"),
        List.of("DEFAULT,1,1,10000,SY,*,*,*,*,*,*,*", "SPACE,2,1,0,SP,*,*,*,*,*,*,*"),
        StandardCharsets.UTF_8);
    // a matrix that covers every non-negative source id (at least 3 left ids), all costs 0
    int rightIds = Math.max(rightId, 1) + 1;
    int leftIds = Math.max(leftId, 2) + 1;
    Files.write(
        dir.resolve("matrix.def"),
        List.of(rightIds + " " + leftIds),
        StandardCharsets.US_ASCII);
    try {
      DictionaryBuilder.build(dir, dir, "utf-8", false);
    } catch (RuntimeException e) {
      return "build fails: " + e.getClass().getSimpleName() + ": " + e.getMessage();
    }
    TokenInfoDictionary dict =
        new TokenInfoDictionary(
            file(dir, TokenInfoDictionary.class, BinaryDictionary.TARGETMAP_FILENAME_SUFFIX),
            file(dir, TokenInfoDictionary.class, BinaryDictionary.POSDICT_FILENAME_SUFFIX),
            file(dir, TokenInfoDictionary.class, BinaryDictionary.DICT_FILENAME_SUFFIX),
            file(dir, TokenInfoDictionary.class, TokenInfoDictionary.FST_FILENAME_SUFFIX));
    IntsRef ref = new IntsRef();
    dict.lookupWordIds(0, ref);
    int wordId = ref.ints[ref.offset];
    String ids = "built; reads back left=" + dict.getLeftId(wordId) + " right=" + dict.getRightId(wordId);
    UnknownDictionary unk =
        new UnknownDictionary(
            file(dir, UnknownDictionary.class, BinaryDictionary.TARGETMAP_FILENAME_SUFFIX),
            file(dir, UnknownDictionary.class, BinaryDictionary.POSDICT_FILENAME_SUFFIX),
            file(dir, UnknownDictionary.class, BinaryDictionary.DICT_FILENAME_SUFFIX));
    ConnectionCosts costs =
        new ConnectionCosts(file(dir, ConnectionCosts.class, ConnectionCosts.FILENAME_SUFFIX));
    List<String> tokens = new ArrayList<>();
    try (KoreanTokenizer tok =
        new KoreanTokenizer(
            TokenStream.DEFAULT_TOKEN_ATTRIBUTE_FACTORY,
            dict,
            unk,
            costs,
            null,
            KoreanTokenizer.DecompoundMode.DISCARD,
            false,
            true)) {
      CharTermAttribute term = tok.addAttribute(CharTermAttribute.class);
      tok.setReader(new StringReader("가나"));
      tok.reset();
      while (tok.incrementToken()) {
        tokens.add(term.toString());
      }
      tok.end();
    } catch (RuntimeException e) {
      return ids + "; tokenizing throws " + e.getClass().getSimpleName();
    }
    return ids + "; tokens " + tokens;
  }

  public static void main(String[] args) throws Exception {
    Path root = Files.createTempDirectory("right-id-bands");
    int[] rightIds = {
      1, 8191, 8192, 12000, 16383, 16384, 16385, 20000, 24575, 24576, 32767, 32768, -1, -8192
    };
    for (int r : rightIds) {
      System.out.println("left=1 right=" + r + " -> " + run(root, 1, r));
    }
    for (int l : new int[] {8191, 8192, -1}) {
      System.out.println("left=" + l + " right=1 -> " + run(root, l, 1));
    }
  }
}
