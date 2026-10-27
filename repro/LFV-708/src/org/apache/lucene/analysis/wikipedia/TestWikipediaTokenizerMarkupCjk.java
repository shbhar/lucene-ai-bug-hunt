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
package org.apache.lucene.analysis.wikipedia;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * WikipediaTokenizer emits Chinese/Japanese characters (its CJ class) only in plain text: the
 * grammar has a {CJ} rule for the initial state alone, while every wiki-markup state (internal and
 * external links, categories, italics, bold, bold italics, headings, templates, and the text that
 * follows a formatting opener) has rules for ALPHANUM only and skips everything else. So every
 * Chinese/Japanese character inside markup is silently dropped: [[日本]], == 日本 ==, ''日本''.
 */
public class TestWikipediaTokenizerMarkupCjk extends BaseTokenStreamTestCase {

  /**
   * Returns the Chinese/Japanese characters (Han, Hiragana, Katakana) of text that lie in no token's
   * offsets, plus the tokens with types. Markup keywords such as "Category" or "ref" are not counted.
   */
  private static String lost(String text) throws Exception {
    WikipediaTokenizer t = new WikipediaTokenizer();
    boolean[] covered = new boolean[text.length()];
    t.setReader(new StringReader(text));
    OffsetAttribute off = t.addAttribute(OffsetAttribute.class);
    CharTermAttribute term = t.addAttribute(CharTermAttribute.class);
    TypeAttribute type = t.addAttribute(TypeAttribute.class);
    List<String> toks = new ArrayList<>();
    t.reset();
    while (t.incrementToken()) {
      for (int i = off.startOffset(); i < off.endOffset(); i++) covered[i] = true;
      toks.add(term + "|" + type.type());
    }
    t.end();
    t.close();
    StringBuilder l = new StringBuilder();
    for (int i = 0; i < text.length(); ) {
      int cp = text.codePointAt(i);
      Character.UnicodeScript sc = Character.UnicodeScript.of(cp);
      boolean cj =
          sc == Character.UnicodeScript.HAN
              || sc == Character.UnicodeScript.HIRAGANA
              || sc == Character.UnicodeScript.KATAKANA;
      if (cj && covered[i] == false) l.appendCodePoint(cp);
      i += Character.charCount(cp);
    }
    return l.length() == 0 ? "" : "lost '" + l + "' from \"" + text + "\", tokens " + toks;
  }

  public void testInternalLink() throws Exception {
    assertEquals("", lost("[[日本]]"));
  }

  public void testCategory() throws Exception {
    assertEquals("", lost("[[Category:日本]]"));
  }

  public void testHeading() throws Exception {
    assertEquals("", lost("== 日本 =="));
  }

  public void testItalics() throws Exception {
    assertEquals("", lost("x ''日本'' y"));
  }

  public void testBold() throws Exception {
    assertEquals("", lost("x '''東京''' y"));
  }

  public void testExternalLinkText() throws Exception {
    assertEquals("", lost("[http://example.org 日本]"));
  }

  public void testCitation() throws Exception {
    assertEquals("", lost("<ref>日本</ref>"));
  }

  /** Controls: plain CJ text, Latin inside markup and Korean inside markup are kept. Pass. */
  public void testControls() throws Exception {
    assertEquals("", lost("日本"));
    assertEquals("", lost("[[Japan]] == Japan == ''Japan''"));
    assertEquals("", lost("[[한국]] == 한국 == ''한국''"));
  }
}
