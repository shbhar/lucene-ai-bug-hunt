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

import static org.apache.lucene.analysis.ko.dict.TokenInfoDictionary.FST_FILENAME_SUFFIX;
import static org.apache.lucene.analysis.morph.BinaryDictionary.DICT_FILENAME_SUFFIX;
import static org.apache.lucene.analysis.morph.BinaryDictionary.POSDICT_FILENAME_SUFFIX;
import static org.apache.lucene.analysis.morph.BinaryDictionary.TARGETMAP_FILENAME_SUFFIX;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ko.KoreanPartOfSpeechStopFilter;
import org.apache.lucene.analysis.ko.KoreanTokenizer;
import org.apache.lucene.analysis.ko.POS;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IntsRef;
import org.apache.lucene.util.IntsRefBuilder;
import org.apache.lucene.util.fst.Util;

/**
 * Independent verifier test (V1). A nori system dictionary source in which two rows share a left
 * context id but carry different parts of speech. The binary format keeps one part of speech per
 * left id (TokenInfoDictionaryEntryWriter.writePosDict, TokenInfoMorphData.getLeftPOS), so such a
 * source cannot be represented. Acceptable outcomes: DictionaryBuilder.build rejects the source
 * with an IllegalArgumentException (as kuromoji's builder does for the same table), or every word
 * keeps the part of speech of its own row. Silently giving one word the other row's part of speech
 * is the defect under test.
 */
public class TestNoriLeftIdPosCollision extends LuceneTestCase {

  private static final String[] UNK_CLASSES = {
    "DEFAULT", "SPACE", "SYMBOL", "NUMERIC", "ALPHA", "CYRILLIC", "GREEK", "HIRAGANA",
    "KATAKANA", "KANJI", "HANGUL", "HANJA", "HANJANUMERIC"
  };

  /** Compiled dictionaries, or null if DictionaryBuilder rejected the source with an IAE. */
  private static final class Compiled {
    final TokenInfoDictionary system;
    final UnknownDictionary unknown;
    final ConnectionCosts costs;

    Compiled(TokenInfoDictionary system, UnknownDictionary unknown, ConnectionCosts costs) {
      this.system = system;
      this.unknown = unknown;
      this.costs = costs;
    }
  }

  private Compiled compile(String... rows) throws IOException {
    Path src = createTempDir("nori-src");
    Path out = createTempDir("nori-bin");
    Files.write(src.resolve("words.csv"), Arrays.asList(rows), StandardCharsets.UTF_8);
    List<String> unk = new ArrayList<>();
    for (String cls : UNK_CLASSES) {
      // unk.def has 10 columns; all unknown entries share left/right id 2 and one part of speech
      unk.add(cls + ",2,2,30000,UNKNOWN,*,*,*,*,*");
    }
    Files.write(src.resolve("unk.def"), unk, StandardCharsets.UTF_8);
    Files.write(src.resolve("char.def"), new byte[0]);
    // 8 x 8 connection matrix, every transition cost 0
    Files.write(src.resolve("matrix.def"), List.of("8 8"), StandardCharsets.US_ASCII);
    try {
      DictionaryBuilder.build(src, out, "utf-8", false);
    } catch (IllegalArgumentException rejected) {
      // rejecting an unrepresentable source is an acceptable outcome
      return null;
    }
    String sys = TokenInfoDictionary.class.getName().replace('.', '/');
    String unkName = UnknownDictionary.class.getName().replace('.', '/');
    String cc = ConnectionCosts.class.getName().replace('.', '/');
    return new Compiled(
        new TokenInfoDictionary(
            out.resolve(sys + TARGETMAP_FILENAME_SUFFIX),
            out.resolve(sys + POSDICT_FILENAME_SUFFIX),
            out.resolve(sys + DICT_FILENAME_SUFFIX),
            out.resolve(sys + FST_FILENAME_SUFFIX)),
        new UnknownDictionary(
            out.resolve(unkName + TARGETMAP_FILENAME_SUFFIX),
            out.resolve(unkName + POSDICT_FILENAME_SUFFIX),
            out.resolve(unkName + DICT_FILENAME_SUFFIX)),
        new ConnectionCosts(out.resolve(cc + ConnectionCosts.FILENAME_SUFFIX)));
  }

  /** Left part of speech of the single entry for {@code surface} in the compiled dictionary. */
  private static POS.Tag leftPOS(TokenInfoDictionary dict, String surface) throws IOException {
    IntsRefBuilder in = new IntsRefBuilder();
    for (int i = 0; i < surface.length(); i++) {
      in.append(surface.charAt(i));
    }
    Long ord = Util.get(dict.getFST().getInternalFST(), in.get());
    assertNotNull("surface not in FST: " + surface, ord);
    IntsRef ids = new IntsRef();
    dict.lookupWordIds(ord.intValue(), ids);
    assertEquals("one entry per surface", 1, ids.length);
    return dict.getMorphAttributes().getLeftPOS(ids.ints[ids.offset]);
  }

  private static List<String> analyze(Compiled c, String text) throws IOException {
    KoreanTokenizer tok =
        new KoreanTokenizer(
            TokenStream.DEFAULT_TOKEN_ATTRIBUTE_FACTORY,
            c.system,
            c.unknown,
            c.costs,
            null,
            KoreanTokenizer.DecompoundMode.DISCARD,
            false,
            true);
    List<String> terms = new ArrayList<>();
    try (TokenStream ts =
        new KoreanPartOfSpeechStopFilter(tok, KoreanPartOfSpeechStopFilter.DEFAULT_STOP_TAGS)) {
      tok.setReader(new StringReader(text));
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        terms.add(term.toString());
      }
      ts.end();
    }
    return terms;
  }

  // 사과 (noun "apple", NNG) and 을 (object particle, JKO) share left id 3.
  // Rows are sorted by surface; 을 (U+C744) sorts after 사과 (U+C0AC), so 을 is written last.
  private static final String NOUN_FIRST_NNG = "사과,3,3,100,NNG,*,F,사과,*,*,*,*";
  private static final String NOUN_FIRST_JKO = "을,3,3,100,JKO,*,T,을,*,*,*,*";

  /** The noun must keep NNG (or the source must be rejected). */
  public void testNounKeepsItsOwnPartOfSpeech() throws Exception {
    Compiled c = compile(NOUN_FIRST_NNG, NOUN_FIRST_JKO);
    if (c == null) {
      return; // rejected with IllegalArgumentException: acceptable
    }
    assertEquals(POS.Tag.JKO, leftPOS(c.system, "을"));
    assertEquals("left POS of 사과", POS.Tag.NNG, leftPOS(c.system, "사과"));
  }

  /** End to end: the default stop tags must not drop the noun. */
  public void testDefaultStopTagsKeepTheNoun() throws Exception {
    Compiled c = compile(NOUN_FIRST_NNG, NOUN_FIRST_JKO);
    if (c == null) {
      return;
    }
    assertEquals(List.of("사과"), analyze(c, "사과"));
  }

  // Reverse direction: 가 (subject particle, JKS) and 나무 (noun "tree", NNG) share left id 4.
  // 나무 (U+B098) sorts after 가 (U+AC00), so the noun is written last.
  private static final String PARTICLE_FIRST_JKS = "가,4,4,100,JKS,*,F,가,*,*,*,*";
  private static final String PARTICLE_FIRST_NNG = "나무,4,4,100,NNG,*,F,나무,*,*,*,*";

  /** The particle must keep JKS, so that the default stop tags remove it. */
  public void testParticleKeepsItsOwnPartOfSpeech() throws Exception {
    Compiled c = compile(PARTICLE_FIRST_JKS, PARTICLE_FIRST_NNG);
    if (c == null) {
      return;
    }
    assertEquals(POS.Tag.NNG, leftPOS(c.system, "나무"));
    assertEquals("left POS of 가", POS.Tag.JKS, leftPOS(c.system, "가"));
    assertEquals(List.of(), analyze(c, "가"));
  }

  /** Control: the same words with distinct left ids are compiled and filtered correctly. */
  public void testControlDistinctLeftIds() throws Exception {
    Compiled c =
        compile(
            "사과,3,3,100,NNG,*,F,사과,*,*,*,*",
            "을,5,5,100,JKO,*,T,을,*,*,*,*",
            "가,4,4,100,JKS,*,F,가,*,*,*,*",
            "나무,6,6,100,NNG,*,F,나무,*,*,*,*");
    assertNotNull("a valid source must not be rejected", c);
    assertEquals(POS.Tag.NNG, leftPOS(c.system, "사과"));
    assertEquals(POS.Tag.JKO, leftPOS(c.system, "을"));
    assertEquals(POS.Tag.JKS, leftPOS(c.system, "가"));
    assertEquals(POS.Tag.NNG, leftPOS(c.system, "나무"));
    assertEquals(List.of("사과"), analyze(c, "사과"));
    assertEquals(List.of(), analyze(c, "을"));
    assertEquals(List.of(), analyze(c, "가"));
    assertEquals(List.of("나무"), analyze(c, "나무"));
  }

  /** Control: sharing a left id with the same part of speech and semantic class is fine. */
  public void testControlSharedLeftIdSamePartOfSpeech() throws Exception {
    Compiled c = compile("사과,3,3,100,NNG,*,F,사과,*,*,*,*", "나무,3,3,100,NNG,*,F,나무,*,*,*,*");
    assertNotNull("a valid source must not be rejected", c);
    assertEquals(POS.Tag.NNG, leftPOS(c.system, "사과"));
    assertEquals(POS.Tag.NNG, leftPOS(c.system, "나무"));
    assertEquals(List.of("사과"), analyze(c, "사과"));
  }
}
