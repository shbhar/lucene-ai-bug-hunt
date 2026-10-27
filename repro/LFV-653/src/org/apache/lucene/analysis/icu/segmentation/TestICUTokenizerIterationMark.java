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
package org.apache.lucene.analysis.icu.segmentation;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * With cjkAsWords=false, ICUTokenizer silently drops the Japanese iteration mark \u3005 (U+3005), so \u4EBA\u3005 ("people"),
 * \u6642\u3005 ("sometimes") and \u69D8\u3005 ("various") lose their second character. U+3005 has Script=Han but is not
 * Ideographic; Default.rbbi removes all Han characters from $ALetterPlus and only gives a rule status to
 * $Ideographic, so its segment gets status 0 and is skipped.
 *
 * <p>Uses only public API: ICUTokenizer, DefaultICUTokenizerConfig, StandardTokenizer.
 */
public class TestICUTokenizerIterationMark extends BaseTokenStreamTestCase {

  private static List<String> terms(Tokenizer t, String text) throws IOException {
    List<String> out = new ArrayList<>();
    t.setReader(new StringReader(text));
    CharTermAttribute term = t.addAttribute(CharTermAttribute.class);
    t.reset();
    while (t.incrementToken()) {
      out.add(term.toString());
    }
    t.end();
    t.close();
    return out;
  }

  private static String esc(String s) {
    StringBuilder sb = new StringBuilder("\"");
    s.codePoints().forEach(cp -> sb.append(cp < 0x80 ? String.valueOf((char) cp) : String.format("\\u{%X}", cp)));
    return sb.append('"').toString();
  }

  /** Every character of the input must be part of some token. */
  private static void assertAllCharsKept(Tokenizer t, String text) throws IOException {
    List<String> tokens = terms(t, text);
    String all = String.join("", tokens);
    List<String> e = new ArrayList<>();
    for (String tok : tokens) e.add(esc(tok));
    for (int i = 0; i < text.length(); i++) {
      assertTrue(
          esc(text.substring(i, i + 1)) + " at offset " + i + " of " + esc(text) + " is missing from the tokens " + e,
          all.indexOf(text.charAt(i)) >= 0);
    }
  }

  private static Tokenizer cjkNotAsWords() {
    return new ICUTokenizer(new DefaultICUTokenizerConfig(false, true));
  }

  /** Controls: the default configuration (dictionary CJK) and StandardTokenizer both keep U+3005. */
  public void testControls() throws Exception {
    assertEquals(List.of("\u4EBA\u3005"), terms(new ICUTokenizer(), "\u4EBA\u3005"));
    assertEquals(List.of("\u4EBA", "\u3005"), terms(new StandardTokenizer(), "\u4EBA\u3005"));
    // another Han character in the same position is kept with cjkAsWords=false
    assertEquals(List.of("\u4EBA", "\u4EBA"), terms(cjkNotAsWords(), "\u4EBA\u4EBA"));
  }

  public void testPeople() throws Exception {
    assertAllCharsKept(cjkNotAsWords(), "\u4EBA\u3005");
  }

  public void testSometimes() throws Exception {
    assertAllCharsKept(cjkNotAsWords(), "\u6642\u3005");
  }

  public void testVariousInSentence() throws Exception {
    assertAllCharsKept(cjkNotAsWords(), "\u69D8\u3005\u306A\u4EBA\u3005");
  }
}
