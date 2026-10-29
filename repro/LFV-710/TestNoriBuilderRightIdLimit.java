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
 * Nori's DictionaryBuilder rejects a left context id >= 8192 but silently accepts a right context
 * id >= 8192, which does not fit the 14 bits the binary format stores it in. Uses only public API:
 * DictionaryBuilder.build, the path constructors of TokenInfoDictionary, UnknownDictionary and
 * ConnectionCosts, and the KoreanTokenizer constructor that takes custom dictionaries.
 */
public class TestNoriBuilderRightIdLimit extends LuceneTestCase {

  private static void write(Path file, String... lines) throws IOException {
    Files.write(file, List.of(lines), StandardCharsets.UTF_8);
  }

  /** Builds a tiny system dictionary; matrix.def lists only the given non-zero cells. */
  private Path build(int rightIdCount, String[] matrixCells, String... rows) throws IOException {
    Path dir = createTempDir();
    write(dir.resolve("test.csv"), rows);
    write(dir.resolve("char.def"), "DEFAULT 0 1 0", "SPACE 0 1 0", "0x0020 SPACE");
    // unknown-word classes get their own left ids (1 and 2), so each left id has one POS
    write(
        dir.resolve("unk.def"),
        "DEFAULT,1,1,10000,SY,*,*,*,*,*,*,*",
        "SPACE,2,1,0,SP,*,*,*,*,*,*,*");
    List<String> matrix = new ArrayList<>();
    matrix.add(rightIdCount + " 3");
    matrix.addAll(List.of(matrixCells));
    Files.write(dir.resolve("matrix.def"), matrix, StandardCharsets.US_ASCII);
    DictionaryBuilder.build(dir, dir, "utf-8", false);
    return dir;
  }

  private static Path file(Path dir, Class<?> clazz, String suffix) {
    return dir.resolve(clazz.getName().replace('.', '/') + suffix);
  }

  private static TokenInfoDictionary systemDict(Path dir) throws IOException {
    return new TokenInfoDictionary(
        file(dir, TokenInfoDictionary.class, BinaryDictionary.TARGETMAP_FILENAME_SUFFIX),
        file(dir, TokenInfoDictionary.class, BinaryDictionary.POSDICT_FILENAME_SUFFIX),
        file(dir, TokenInfoDictionary.class, BinaryDictionary.DICT_FILENAME_SUFFIX),
        file(dir, TokenInfoDictionary.class, TokenInfoDictionary.FST_FILENAME_SUFFIX));
  }

  private static List<String> tokenize(Path dir, String text) throws IOException {
    UnknownDictionary unk =
        new UnknownDictionary(
            file(dir, UnknownDictionary.class, BinaryDictionary.TARGETMAP_FILENAME_SUFFIX),
            file(dir, UnknownDictionary.class, BinaryDictionary.POSDICT_FILENAME_SUFFIX),
            file(dir, UnknownDictionary.class, BinaryDictionary.DICT_FILENAME_SUFFIX));
    ConnectionCosts costs = new ConnectionCosts(file(dir, ConnectionCosts.class, ".dat"));
    List<String> out = new ArrayList<>();
    try (KoreanTokenizer tok =
        new KoreanTokenizer(
            TokenStream.DEFAULT_TOKEN_ATTRIBUTE_FACTORY,
            systemDict(dir),
            unk,
            costs,
            null,
            KoreanTokenizer.DecompoundMode.DISCARD,
            false,
            true)) {
      CharTermAttribute term = tok.addAttribute(CharTermAttribute.class);
      tok.setReader(new StringReader(text));
      tok.reset();
      while (tok.incrementToken()) {
        out.add(term.toString());
      }
      tok.end();
    }
    return out;
  }

  /** Control: a left id of 8192 is rejected when the dictionary is built. */
  public void testLeftId8192IsRejected() {
    IllegalArgumentException e =
        expectThrows(
            IllegalArgumentException.class,
            () -> build(8193, new String[0], "가나,8192,1,100,NNG,*,F,가나,*,*,*,*"));
    assertTrue(e.getMessage(), e.getMessage().contains("leftId >= 8192"));
  }

  /**
   * The source row says right id 16385. Either the builder rejects it, as it rejects a left id that
   * large, or the compiled dictionary returns 16385.
   */
  public void testRightIdRoundTrip() throws Exception {
    Path dir;
    try {
      dir = build(16386, new String[0], "가나,1,16385,100,NNG,*,F,가나,*,*,*,*");
    } catch (IllegalArgumentException rejected) {
      return; // rejecting the row is a correct outcome
    }
    TokenInfoDictionary dict = systemDict(dir);
    IntsRef ref = new IntsRef();
    dict.lookupWordIds(0, ref); // the only surface has source id 0
    assertEquals(1, ref.length);
    int wordId = ref.ints[ref.offset];
    assertEquals("left id", 1, dict.getLeftId(wordId));
    assertEquals("word cost", 100, dict.getWordCost(wordId));
    assertEquals("right id", 16385, dict.getRightId(wordId));
  }

  /**
   * With the right id kept, 가나 followed by any word costs 20000, so the cheapest analysis of 가나다
   * is 가 + 나다 (cost 2000). If the right id silently becomes 1, the tokenizer picks 가나 + 다.
   */
  public void testRightIdChangesSegmentation() throws Exception {
    Path dir;
    try {
      dir =
          build(
              16386,
              new String[] {"16385 1 20000"},
              "가나,1,16385,0,NNG,*,F,가나,*,*,*,*",
              "다,1,1,0,NNG,*,F,다,*,*,*,*",
              "가,1,1,1000,NNG,*,F,가,*,*,*,*",
              "나다,1,1,1000,NNG,*,F,나다,*,*,*,*");
    } catch (IllegalArgumentException rejected) {
      return;
    }
    assertEquals(List.of("가", "나다"), tokenize(dir, "가나다"));
  }

  /** A right id in [8192, 16383] makes tokenization of the word throw instead. */
  public void testRightId8192Tokenizes() throws Exception {
    Path dir;
    try {
      dir = build(8193, new String[0], "가나,1,8192,100,NNG,*,F,가나,*,*,*,*");
    } catch (IllegalArgumentException rejected) {
      return;
    }
    assertEquals(List.of("가나"), tokenize(dir, "가나"));
  }
}
