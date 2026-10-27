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
import org.apache.lucene.analysis.ko.KoreanPartOfSpeechStopFilter;
import org.apache.lucene.analysis.ko.KoreanTokenizer;
import org.apache.lucene.analysis.ko.POS;
import org.apache.lucene.analysis.ko.util.DictionaryBuilder;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IntsRef;

/**
 * Variant for 9.x (DictionaryBuilder in ko.util). Nori stores one part of speech per left context id. Two dictionary rows with the same left id but
 * different parts of speech are checked only by a Java assert in the dictionary builder, so a build
 * without -ea silently gives every word with that left id the part of speech of the last row
 * written. (Kuromoji's builder throws IllegalArgumentException for the same situation.) Uses only
 * public API: DictionaryBuilder.build, the path constructors of the dictionaries, KoreanTokenizer
 * and KoreanPartOfSpeechStopFilter.
 */
public class TestNoriBuilderLeftIdPosCollision9x extends LuceneTestCase {

  private static Path file(Path dir, Class<?> clazz, String suffix) {
    return dir.resolve(clazz.getName().replace('.', '/') + suffix);
  }

  /** 가 (a noun, NNG) and 는 (a topic particle, JX) share left id 1. */
  private Path build() throws IOException {
    Path dir = createTempDir();
    Files.write(
        dir.resolve("test.csv"),
        List.of("가,1,1,0,NNG,*,F,가,*,*,*,*", "는,1,1,0,J,*,T,는,*,*,*,*"),
        StandardCharsets.UTF_8);
    Files.write(dir.resolve("char.def"), List.of("DEFAULT 0 1 0", "SPACE 0 1 0", "0x0020 SPACE"));
    Files.write(
        dir.resolve("unk.def"),
        List.of("DEFAULT,2,2,10000,SY,*,*,*,*,*,*,*", "SPACE,3,3,0,SP,*,*,*,*,*,*,*"),
        StandardCharsets.UTF_8);
    Files.write(dir.resolve("matrix.def"), List.of("4 4"), StandardCharsets.US_ASCII);
    DictionaryBuilder.build(dir, dir, "utf-8", false);
    return dir;
  }

  private static TokenInfoDictionary systemDict(Path dir) throws IOException {
    return new TokenInfoDictionary(
        file(dir, TokenInfoDictionary.class, "$targetMap.dat"),
        file(dir, TokenInfoDictionary.class, "$posDict.dat"),
        file(dir, TokenInfoDictionary.class, "$buffer.dat"),
        file(dir, TokenInfoDictionary.class, "$fst.dat"));
  }

  /** Either the builder rejects the rows, or each word keeps the part of speech of its row. */
  public void testEachWordKeepsItsPartOfSpeech() throws Exception {
    Path dir;
    try {
      dir = build();
    } catch (IllegalArgumentException rejected) {
      return; // rejecting the dictionary, as kuromoji does, is a correct outcome
    }
    TokenInfoDictionary dict = systemDict(dir);
    IntsRef ref = new IntsRef();
    dict.lookupWordIds(0, ref); // source id 0 = 가 (surfaces are sorted)
    assertEquals(POS.Tag.NNG, dict.getLeftPOS(ref.ints[ref.offset]));
    dict.lookupWordIds(1, ref); // source id 1 = 는
    assertEquals(POS.Tag.J, dict.getLeftPOS(ref.ints[ref.offset]));
  }

  /** The noun 가 must survive the default part-of-speech stop filter (J is a stop tag in 9.x, NNG is not). */
  public void testNounSurvivesDefaultStopTags() throws Exception {
    Path dir;
    try {
      dir = build();
    } catch (IllegalArgumentException rejected) {
      return;
    }
    UnknownDictionary unk =
        new UnknownDictionary(
            file(dir, UnknownDictionary.class, "$targetMap.dat"),
            file(dir, UnknownDictionary.class, "$posDict.dat"),
            file(dir, UnknownDictionary.class, "$buffer.dat"));
    ConnectionCosts costs = new ConnectionCosts(file(dir, ConnectionCosts.class, ".dat"));
    KoreanTokenizer tok =
        new KoreanTokenizer(
            TokenStream.DEFAULT_TOKEN_ATTRIBUTE_FACTORY,
            systemDict(dir),
            unk,
            costs,
            null,
            KoreanTokenizer.DecompoundMode.DISCARD,
            false,
            true);
    List<String> out = new ArrayList<>();
    try (TokenStream ts =
        new KoreanPartOfSpeechStopFilter(tok, KoreanPartOfSpeechStopFilter.DEFAULT_STOP_TAGS)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      tok.setReader(new StringReader("가"));
      ts.reset();
      while (ts.incrementToken()) {
        out.add(term.toString());
      }
      ts.end();
    }
    assertEquals(List.of("가"), out);
  }
}
