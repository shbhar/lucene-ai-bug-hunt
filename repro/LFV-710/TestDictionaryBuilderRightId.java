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

import java.io.IOException;
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
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IntsRef;

/**
 * DictionaryBuilder stores each context id in 14 bits. It rejects a left id of 8192 or more; a
 * right id must likewise be rejected, or read back as written. Public API only.
 */
public class TestDictionaryBuilderRightId extends LuceneTestCase {

  /**
   * Builds a system dictionary from MeCab-format rows. matrix.def declares {@code rightIds} right
   * ids and 3 left ids and lists the given non-zero costs ("rightId leftId cost"). Returns null if
   * DictionaryBuilder rejects the source with IllegalArgumentException.
   */
  private Path build(int rightIds, List<String> costs, String... rows) throws IOException {
    Path dir = createTempDir();
    Files.write(dir.resolve("test.csv"), List.of(rows), StandardCharsets.UTF_8);
    Files.write(
        dir.resolve("char.def"),
        List.of("DEFAULT 0 1 0", "SPACE 0 1 0", "0x0020 SPACE"),
        StandardCharsets.UTF_8);
    Files.write(
        dir.resolve("unk.def"),
        List.of("DEFAULT,1,1,10000,SY,*,*,*,*,*,*,*", "SPACE,2,1,0,SP,*,*,*,*,*,*,*"),
        StandardCharsets.UTF_8);
    List<String> matrix = new ArrayList<>();
    matrix.add(rightIds + " 3");
    matrix.addAll(costs);
    Files.write(dir.resolve("matrix.def"), matrix, StandardCharsets.US_ASCII);
    try {
      DictionaryBuilder.build(dir, dir, "utf-8", false);
    } catch (IllegalArgumentException rejected) {
      return null;
    }
    return dir;
  }

  private static Path file(Path dir, Class<?> clazz, String suffix) {
    return dir.resolve(clazz.getName().replace('.', '/') + suffix);
  }

  private static TokenInfoDictionary systemDictionary(Path dir) throws IOException {
    return new TokenInfoDictionary(
        file(dir, TokenInfoDictionary.class, BinaryDictionary.TARGETMAP_FILENAME_SUFFIX),
        file(dir, TokenInfoDictionary.class, BinaryDictionary.POSDICT_FILENAME_SUFFIX),
        file(dir, TokenInfoDictionary.class, BinaryDictionary.DICT_FILENAME_SUFFIX),
        file(dir, TokenInfoDictionary.class, TokenInfoDictionary.FST_FILENAME_SUFFIX));
  }

  /** The right id the compiled dictionary returns for its only word. */
  private static int compiledRightId(Path dir) throws IOException {
    TokenInfoDictionary dict = systemDictionary(dir);
    IntsRef wordIds = new IntsRef();
    dict.lookupWordIds(0, wordIds);
    assertEquals(1, wordIds.length);
    return dict.getRightId(wordIds.ints[wordIds.offset]);
  }

  private static List<String> tokenize(Path dir, String text) throws IOException {
    UnknownDictionary unk =
        new UnknownDictionary(
            file(dir, UnknownDictionary.class, BinaryDictionary.TARGETMAP_FILENAME_SUFFIX),
            file(dir, UnknownDictionary.class, BinaryDictionary.POSDICT_FILENAME_SUFFIX),
            file(dir, UnknownDictionary.class, BinaryDictionary.DICT_FILENAME_SUFFIX));
    ConnectionCosts costs =
        new ConnectionCosts(file(dir, ConnectionCosts.class, ConnectionCosts.FILENAME_SUFFIX));
    List<String> tokens = new ArrayList<>();
    try (KoreanTokenizer tokenizer =
        new KoreanTokenizer(
            TokenStream.DEFAULT_TOKEN_ATTRIBUTE_FACTORY,
            systemDictionary(dir),
            unk,
            costs,
            null,
            KoreanTokenizer.DecompoundMode.DISCARD,
            false,
            true)) {
      CharTermAttribute term = tokenizer.addAttribute(CharTermAttribute.class);
      tokenizer.setReader(new StringReader(text));
      tokenizer.reset();
      while (tokenizer.incrementToken()) {
        tokens.add(term.toString());
      }
      tokenizer.end();
    }
    return tokens;
  }

  /** Control: an overlarge left id is rejected. */
  public void testLeftId8192IsRejected() throws IOException {
    assertNull(build(8193, List.of(), "가나,8192,1,100,NNG,*,F,가나,*,*,*,*"));
  }

  /** Control: the largest right id that fits is kept and usable. */
  public void testRightId8191IsKept() throws IOException {
    Path dir = build(8192, List.of(), "가나,1,8191,100,NNG,*,F,가나,*,*,*,*");
    assertEquals(8191, compiledRightId(dir));
    assertEquals(List.of("가나"), tokenize(dir, "가나"));
  }

  public void testRightId16385IsRejectedOrKept() throws IOException {
    Path dir = build(16386, List.of(), "가나,1,16385,100,NNG,*,F,가나,*,*,*,*");
    if (dir != null) {
      assertEquals("right id", 16385, compiledRightId(dir));
    }
  }

  public void testNegativeRightIdIsRejectedOrKept() throws IOException {
    Path dir = build(8192, List.of(), "가나,1,-1,100,NNG,*,F,가나,*,*,*,*");
    if (dir != null) {
      assertEquals("right id", -1, compiledRightId(dir));
    }
  }

  /**
   * After 가나 (right id 16385) every word costs 20000, so 가나다 should split as 가 + 나다 (total cost
   * 2000). If the right id is stored as 1, 가나 + 다 costs 0 and wins.
   */
  public void testRightId16385Segmentation() throws IOException {
    Path dir =
        build(
            16386,
            List.of("16385 1 20000"),
            "가나,1,16385,0,NNG,*,F,가나,*,*,*,*",
            "다,1,1,0,NNG,*,F,다,*,*,*,*",
            "가,1,1,1000,NNG,*,F,가,*,*,*,*",
            "나다,1,1,1000,NNG,*,F,나다,*,*,*,*");
    if (dir != null) {
      assertEquals(List.of("가", "나다"), tokenize(dir, "가나다"));
    }
  }

  public void testRightId8192Tokenizes() throws IOException {
    Path dir = build(8193, List.of(), "가나,1,8192,100,NNG,*,F,가나,*,*,*,*");
    if (dir != null) {
      assertEquals(List.of("가나"), tokenize(dir, "가나"));
    }
  }
}
