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
package org.apache.lucene.analysis.ja.dict;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ja.JapaneseTokenizer;
import org.apache.lucene.analysis.ja.tokenattributes.ReadingAttribute;
import org.apache.lucene.analysis.morph.BinaryDictionary;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IntsRef;

/**
 * Kuromoji's DictionaryBuilder rejects a base form of 16 or more chars but stores the length of a
 * reading or pronunciation in 7 bits of a byte without a check, so a reading of 128 or more chars
 * is silently written with a wrapped length and the dictionary returns a wrong reading and a wrong
 * pronunciation. Uses only public API: DictionaryBuilder.build, the dictionaries' path
 * constructors and the JapaneseTokenizer constructor that takes custom dictionaries.
 */
public class TestKuromojiBuilderReadingLength extends LuceneTestCase {

  private static final String READING = "カ".repeat(64) + "ン".repeat(64); // 128 katakana
  private static final String PRONUNCIATION = "カン";

  private static Path file(Path dir, Class<?> clazz, String suffix) {
    return dir.resolve(clazz.getName().replace('.', '/') + suffix);
  }

  private Path build(String row) throws IOException {
    Path dir = createTempDir();
    Files.write(dir.resolve("test.csv"), List.of(row), StandardCharsets.UTF_8);
    Files.write(dir.resolve("char.def"), List.of("DEFAULT 0 1 0"), StandardCharsets.UTF_8);
    Files.write(dir.resolve("unk.def"), List.of("DEFAULT,1,1,10000,記号,一般,*,*,*,*,*"), StandardCharsets.UTF_8);
    Files.write(dir.resolve("matrix.def"), List.of("2 2"), StandardCharsets.US_ASCII);
    DictionaryBuilder.build(DictionaryBuilder.DictionaryFormat.IPADIC, dir, dir, "utf-8", false);
    return dir;
  }

  private static TokenInfoDictionary systemDict(Path dir) throws IOException {
    return new TokenInfoDictionary(
        file(dir, TokenInfoDictionary.class, BinaryDictionary.TARGETMAP_FILENAME_SUFFIX),
        file(dir, TokenInfoDictionary.class, BinaryDictionary.POSDICT_FILENAME_SUFFIX),
        file(dir, TokenInfoDictionary.class, BinaryDictionary.DICT_FILENAME_SUFFIX),
        file(dir, TokenInfoDictionary.class, TokenInfoDictionary.FST_FILENAME_SUFFIX));
  }

  /** Control: a base form of 16 chars is rejected when the dictionary is built. */
  public void testLongBaseFormIsRejected() {
    String base = "漢".repeat(16);
    IllegalArgumentException e =
        expectThrows(
            IllegalArgumentException.class,
            () -> build("漢字,1,1,1,名詞,一般,*,*,*,*," + base + ",カンジ,カンジ"));
    assertTrue(e.getMessage(), e.getMessage().contains("is >= 16"));
  }

  /** Either the builder rejects a 128-char reading, or the dictionary returns the source values. */
  public void testLongReadingRoundTrip() throws Exception {
    Path dir;
    try {
      dir = build("漢字,1,1,1,名詞,一般,*,*,*,*,漢字," + READING + "," + PRONUNCIATION);
    } catch (IllegalArgumentException rejected) {
      return; // rejecting the row, as for a long base form, is a correct outcome
    }
    TokenInfoDictionary dict = systemDict(dir);
    IntsRef ref = new IntsRef();
    dict.lookupWordIds(0, ref);
    int wordId = ref.ints[ref.offset];
    char[] surface = "漢字".toCharArray();
    assertEquals(READING, dict.getMorphAttributes().getReading(wordId, surface, 0, 2));
    assertEquals(PRONUNCIATION, dict.getMorphAttributes().getPronunciation(wordId, surface, 0, 2));
  }

  /** The same through JapaneseTokenizer's ReadingAttribute. */
  public void testTokenizerReading() throws Exception {
    Path dir;
    try {
      dir = build("漢字,1,1,1,名詞,一般,*,*,*,*,漢字," + READING + "," + PRONUNCIATION);
    } catch (IllegalArgumentException rejected) {
      return;
    }
    UnknownDictionary unk =
        new UnknownDictionary(
            file(dir, UnknownDictionary.class, BinaryDictionary.TARGETMAP_FILENAME_SUFFIX),
            file(dir, UnknownDictionary.class, BinaryDictionary.POSDICT_FILENAME_SUFFIX),
            file(dir, UnknownDictionary.class, BinaryDictionary.DICT_FILENAME_SUFFIX));
    ConnectionCosts costs = new ConnectionCosts(file(dir, ConnectionCosts.class, ".dat"));
    try (JapaneseTokenizer tok =
        new JapaneseTokenizer(
            TokenStream.DEFAULT_TOKEN_ATTRIBUTE_FACTORY,
            systemDict(dir),
            unk,
            costs,
            null,
            true,
            true,
            JapaneseTokenizer.Mode.NORMAL)) {
      ReadingAttribute reading = tok.addAttribute(ReadingAttribute.class);
      tok.setReader(new StringReader("漢字"));
      tok.reset();
      assertTrue(tok.incrementToken());
      assertEquals(READING, reading.getReading());
      assertEquals(PRONUNCIATION, reading.getPronunciation());
      tok.end();
    }
  }
}
