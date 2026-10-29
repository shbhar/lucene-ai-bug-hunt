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

import static org.apache.lucene.analysis.ja.dict.TokenInfoDictionary.FST_FILENAME_SUFFIX;
import static org.apache.lucene.analysis.morph.BinaryDictionary.DICT_FILENAME_SUFFIX;
import static org.apache.lucene.analysis.morph.BinaryDictionary.POSDICT_FILENAME_SUFFIX;
import static org.apache.lucene.analysis.morph.BinaryDictionary.TARGETMAP_FILENAME_SUFFIX;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ja.JapaneseReadingFormFilter;
import org.apache.lucene.analysis.ja.JapaneseTokenizer;
import org.apache.lucene.analysis.ja.tokenattributes.ReadingAttribute;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * DictionaryBuilder stores the length of a reading and of a pronunciation in 7 bits. A row whose
 * reading or pronunciation is longer must be rejected when the dictionary is built, as a base form
 * of 16 or more chars is, or be read back intact. Public API only.
 */
public class TestDictionaryBuilderLongReading extends LuceneTestCase {

  // a 64-char proper noun, its 136-char reading, and its pronunciation (long vowels written as ー)
  private static final String SURFACE = "国立研究開発法人".repeat(8);
  private static final String READING = "コクリツケンキュウカイハツホウジン".repeat(8);
  private static final String PRONUNCIATION = "コクリツケンキューカイハツホージン".repeat(8);

  /** Control: the builder rejects a base form of 16 chars, which its format cannot hold. */
  public void testLongBaseFormIsRejected() {
    String baseForm = "漢".repeat(16);
    IllegalArgumentException e =
        expectThrows(
            IllegalArgumentException.class, () -> build(row("漢字", baseForm, "カンジ", "カンジ")));
    assertTrue(e.getMessage(), e.getMessage().contains("is >= 16"));
  }

  /** Control: 127 chars, the most a 7-bit length holds, round-trip. */
  public void testReadingOf127Chars() throws Exception {
    String reading = READING.substring(0, 127);
    String pronunciation = PRONUNCIATION.substring(0, 127);
    Path dir = build(row(SURFACE, SURFACE, reading, pronunciation));
    assertEquals(List.of(reading, pronunciation, reading), analyze(dir, SURFACE));
  }

  /** 128 chars: today the reading, and the term that JapaneseReadingFormFilter indexes, are "". */
  public void testReadingOf128Chars() throws Exception {
    String reading = READING.substring(0, 128);
    assertReadBackOrRejected(reading, reading);
  }

  /** 136 chars: today the reading is its first 8 chars and the pronunciation is garbage. */
  public void testReadingOf136CharsWithPronunciation() throws Exception {
    assertReadBackOrRejected(READING, PRONUNCIATION);
  }

  private void assertReadBackOrRejected(String reading, String pronunciation) throws Exception {
    Path dir;
    try {
      dir = build(row(SURFACE, SURFACE, reading, pronunciation));
    } catch (IllegalArgumentException e) {
      // rejecting the row, as the builder does for a long base form, is a correct outcome
      String msg = e.getMessage();
      assertTrue(msg, msg.contains("reading") || msg.contains("pronunciation"));
      return;
    }
    List<String> got = analyze(dir, SURFACE);
    assertEquals(
        "[reading, pronunciation, indexed term], lengths "
            + got.stream().map(String::length).toList(),
        List.of(reading, pronunciation, reading),
        got);
  }

  private static String row(String surface, String baseForm, String reading, String pron) {
    return surface + ",1,1,1,名詞,固有名詞,組織,*,*,*," + baseForm + "," + reading + "," + pron;
  }

  private Path build(String row) throws IOException {
    Path dir = createTempDir();
    Files.write(dir.resolve("test.csv"), List.of(row), StandardCharsets.UTF_8);
    Files.write(dir.resolve("char.def"), List.of("DEFAULT 0 1 0"), StandardCharsets.UTF_8);
    Files.write(
        dir.resolve("unk.def"),
        List.of("DEFAULT,1,1,10000,記号,一般,*,*,*,*,*"),
        StandardCharsets.UTF_8);
    Files.write(dir.resolve("matrix.def"), List.of("2 2"), StandardCharsets.UTF_8);
    DictionaryBuilder.build(DictionaryBuilder.DictionaryFormat.IPADIC, dir, dir, "utf-8", false);
    return dir;
  }

  /** Tokenizes the text with the built dictionary: [reading, pronunciation, indexed term]. */
  private static List<String> analyze(Path dir, String text) throws IOException {
    String sys = TokenInfoDictionary.class.getName().replace('.', '/');
    String unk = UnknownDictionary.class.getName().replace('.', '/');
    String cc = ConnectionCosts.class.getName().replace('.', '/');
    JapaneseTokenizer tokenizer =
        new JapaneseTokenizer(
            TokenStream.DEFAULT_TOKEN_ATTRIBUTE_FACTORY,
            new TokenInfoDictionary(
                dir.resolve(sys + TARGETMAP_FILENAME_SUFFIX),
                dir.resolve(sys + POSDICT_FILENAME_SUFFIX),
                dir.resolve(sys + DICT_FILENAME_SUFFIX),
                dir.resolve(sys + FST_FILENAME_SUFFIX)),
            new UnknownDictionary(
                dir.resolve(unk + TARGETMAP_FILENAME_SUFFIX),
                dir.resolve(unk + POSDICT_FILENAME_SUFFIX),
                dir.resolve(unk + DICT_FILENAME_SUFFIX)),
            new ConnectionCosts(dir.resolve(cc + ConnectionCosts.FILENAME_SUFFIX)),
            null,
            true,
            true,
            JapaneseTokenizer.Mode.NORMAL);
    tokenizer.setReader(new StringReader(text));
    try (TokenStream ts = new JapaneseReadingFormFilter(tokenizer, false)) {
      ReadingAttribute reading = ts.addAttribute(ReadingAttribute.class);
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      assertTrue(ts.incrementToken());
      List<String> out = List.of(reading.getReading(), reading.getPronunciation(), term.toString());
      assertFalse("expected one token", ts.incrementToken());
      ts.end();
      return out;
    }
  }
}
