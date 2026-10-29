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
import java.util.Arrays;
import java.util.List;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ko.KoreanTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IntsRef;

/**
 * Nori's DictionaryBuilder rejects a left context id that does not fit the 14-bit id field of the
 * compiled dictionary, but it does not check the right context id, which is stored in the same
 * kind of 14-bit field. Every test asserts the same rule: a source row is either rejected when the
 * dictionary is built (IllegalArgumentException, as for a too-large left id), or the compiled
 * dictionary behaves as the source row says. Uses only public API.
 */
public class TestNoriRightIdRange extends LuceneTestCase {

  private static final String GA = "가"; // Hangul syllable GA
  private static final String NA = "나"; // Hangul syllable NA

  // matrix.def dimensions: 16386 right ids (forward), 1802 left ids (backward). Every id used
  // below, including the builder's own built-in NGRAM unknown entry (left 1801, right 3559), is
  // inside the declared matrix, so the source is a consistent MeCab-format dictionary.
  private static final String MATRIX_DIMS = "16386 1802";

  private static String row(String surface, int leftId, int rightId, int cost) {
    return surface + "," + leftId + "," + rightId + "," + cost + ",NNG,*,F,*,*,*,*,*";
  }

  /** Builds a dictionary; returns null if DictionaryBuilder rejects the source. */
  private Path build(List<String> rows, String... matrixCosts) throws IOException {
    Path src = createTempDir("nori-src");
    Path out = createTempDir("nori-out");
    Files.write(src.resolve("words.csv"), rows, StandardCharsets.UTF_8);
    List<String> unk = new ArrayList<>();
    for (String cls :
        new String[] {
          "DEFAULT", "SPACE", "SYMBOL", "NUMERIC", "ALPHA", "CYRILLIC", "GREEK", "HIRAGANA",
          "KATAKANA", "KANJI", "HANGUL", "HANJA", "HANJANUMERIC"
        }) {
      // unknown words: legal ids, very expensive, so they never win
      unk.add(cls + ",0,0,30000,SY,*,*,*,*,*");
    }
    Files.write(src.resolve("unk.def"), unk, StandardCharsets.UTF_8);
    Files.write(src.resolve("char.def"), new ArrayList<String>(), StandardCharsets.UTF_8);
    List<String> matrix = new ArrayList<>();
    matrix.add(MATRIX_DIMS);
    matrix.addAll(Arrays.asList(matrixCosts));
    Files.write(src.resolve("matrix.def"), matrix, StandardCharsets.US_ASCII);
    try {
      DictionaryBuilder.build(src, out, "UTF-8", false);
    } catch (IllegalArgumentException rejected) {
      return null;
    }
    return out;
  }

  private static Path file(Path out, Class<?> clazz, String suffix) {
    return out.resolve(clazz.getName().replace('.', '/') + suffix);
  }

  private static TokenInfoDictionary systemDict(Path out) throws IOException {
    return new TokenInfoDictionary(
        file(out, TokenInfoDictionary.class, TokenInfoDictionary.TARGETMAP_FILENAME_SUFFIX),
        file(out, TokenInfoDictionary.class, TokenInfoDictionary.POSDICT_FILENAME_SUFFIX),
        file(out, TokenInfoDictionary.class, TokenInfoDictionary.DICT_FILENAME_SUFFIX),
        file(out, TokenInfoDictionary.class, TokenInfoDictionary.FST_FILENAME_SUFFIX));
  }

  private static List<String> tokenize(Path out, String text) throws IOException {
    UnknownDictionary unk =
        new UnknownDictionary(
            file(out, UnknownDictionary.class, UnknownDictionary.TARGETMAP_FILENAME_SUFFIX),
            file(out, UnknownDictionary.class, UnknownDictionary.POSDICT_FILENAME_SUFFIX),
            file(out, UnknownDictionary.class, UnknownDictionary.DICT_FILENAME_SUFFIX));
    ConnectionCosts costs =
        new ConnectionCosts(file(out, ConnectionCosts.class, ConnectionCosts.FILENAME_SUFFIX));
    List<String> tokens = new ArrayList<>();
    try (KoreanTokenizer tokenizer =
        new KoreanTokenizer(
            TokenStream.DEFAULT_TOKEN_ATTRIBUTE_FACTORY,
            systemDict(out),
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

  /** Returns {leftId, rightId} of the single entry of a one-row dictionary. */
  private static int[] ids(Path out) throws IOException {
    TokenInfoDictionary dict = systemDict(out);
    IntsRef ref = new IntsRef();
    dict.lookupWordIds(0, ref);
    assertEquals(1, ref.length);
    int wordId = ref.ints[ref.offset];
    return new int[] {dict.getLeftId(wordId), dict.getRightId(wordId)};
  }

  private void assertRightIdRoundTripsOrRejected(int rightId) throws IOException {
    Path out = build(List.of(row(GA, 1, rightId, 0)));
    if (out == null) {
      return; // rejected at build time: acceptable
    }
    int[] ids = ids(out);
    assertEquals("left id", 1, ids[0]);
    assertEquals("right id", rightId, ids[1]);
  }

  // ---- controls (expected to pass) ----

  public void testControlLeftId8192IsRejected() throws IOException {
    assertNull("a left id of 8192 must be rejected", build(List.of(row(GA, 8192, 1, 0))));
  }

  public void testControlRightId8191RoundTrips() throws IOException {
    Path out = build(List.of(row(GA, 1, 8191, 0)));
    assertNotNull(out);
    assertEquals(8191, ids(out)[1]);
    assertEquals(List.of(GA), tokenize(out, GA));
  }

  // ---- the claim ----

  public void testRightId8192RoundTripsOrIsRejected() throws IOException {
    assertRightIdRoundTripsOrRejected(8192);
  }

  public void testRightId16385RoundTripsOrIsRejected() throws IOException {
    assertRightIdRoundTripsOrRejected(16385);
  }

  /** A word whose right id is 8192 must either be rejected or be usable by the tokenizer. */
  public void testTokenizeWordWithRightId8192() throws IOException {
    Path out = build(List.of(row(GA, 1, 8192, 0)));
    if (out == null) {
      return;
    }
    assertEquals(List.of(GA), tokenize(out, GA));
  }

  /**
   * Text GA NA, words GA (right id R), NA (left id 2) and GA+NA (cost 5000). The matrix makes the
   * transition from right id 1 to left id 2 cost 10000 and every other transition free. With R =
   * 16385 the path [GA, NA] costs 0 and must win over [GA+NA] (5000). If the right id is stored as
   * 16385 mod 16384 = 1, [GA, NA] costs 10000 and the tokenizer outputs [GA+NA].
   */
  private List<String> segment(int rightIdOfGa) throws IOException {
    Path out =
        build(
            List.of(row(GA, 1, rightIdOfGa, 0), row(NA, 2, 2, 0), row(GA + NA, 1, 2, 5000)),
            "1 2 10000");
    if (out == null) {
      return null;
    }
    return tokenize(out, GA + NA);
  }

  public void testControlSegmentationWithSmallRightIds() throws IOException {
    assertEquals(List.of(GA, NA), segment(3)); // free transition: two words
    assertEquals(List.of(GA + NA), segment(1)); // expensive transition: one word
  }

  public void testSegmentationUsesTheSourceRightId() throws IOException {
    List<String> tokens = segment(16385);
    if (tokens == null) {
      return; // rejected at build time: acceptable
    }
    assertEquals(List.of(GA, NA), tokens);
  }
}
