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

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * Chinese and Japanese text inside wiki markup must not vanish from WikipediaTokenizer's tokens.
 * The same markup around Latin or Korean text, and Chinese or Japanese text outside markup, are
 * tokenized.
 */
public class TestWikipediaTokenizerCjkInMarkup extends BaseTokenStreamTestCase {

  /**
   * Fails if a Han, Hiragana or Katakana character of the text lies in no token's offsets. It does
   * not care how the text is split or typed.
   */
  private static void assertNoCjkLost(int tokenOutput, Set<String> untokenizedTypes, String text)
      throws IOException {
    boolean[] covered = new boolean[text.length()];
    List<String> tokens = new ArrayList<>();
    try (WikipediaTokenizer tokenizer = new WikipediaTokenizer(tokenOutput, untokenizedTypes)) {
      CharTermAttribute term = tokenizer.addAttribute(CharTermAttribute.class);
      OffsetAttribute offset = tokenizer.addAttribute(OffsetAttribute.class);
      TypeAttribute type = tokenizer.addAttribute(TypeAttribute.class);
      tokenizer.setReader(new StringReader(text));
      tokenizer.reset();
      while (tokenizer.incrementToken()) {
        for (int i = offset.startOffset(); i < offset.endOffset(); i++) {
          covered[i] = true;
        }
        tokens.add(term + "|" + type.type());
      }
      tokenizer.end();
    }
    StringBuilder lost = new StringBuilder();
    for (int i = 0; i < text.length(); i++) {
      Character.UnicodeScript script = Character.UnicodeScript.of(text.charAt(i));
      boolean cjk =
          script == Character.UnicodeScript.HAN
              || script == Character.UnicodeScript.HIRAGANA
              || script == Character.UnicodeScript.KATAKANA;
      if (cjk && covered[i] == false) {
        lost.append(text.charAt(i));
      }
    }
    assertEquals("lost from \"" + text + "\", tokens " + tokens, "", lost.toString());
  }

  private static void assertNoCjkLost(String text) throws IOException {
    assertNoCjkLost(WikipediaTokenizer.TOKENS_ONLY, Collections.emptySet(), text);
  }

  /** Controls: these pass on affected versions too. */
  public void testControls() throws Exception {
    assertNoCjkLost("東京は日本の首都である。"); // plain text: 11 <CJ> tokens
    assertNoCjkLost("Tokyo is the capital of [[Japan]]."); // Japan|il
    assertNoCjkLost("[[한국]] == 한국 == x '''한국''' y"); // Korean inside markup is kept
  }

  public void testInternalLink() throws Exception {
    assertNoCjkLost("東京は[[日本]]の首都である。");
  }

  public void testCategory() throws Exception {
    assertNoCjkLost("[[Category:日本]]");
  }

  public void testHeading() throws Exception {
    assertNoCjkLost("== 歴史 ==");
  }

  public void testItalicsAndBold() throws Exception {
    assertNoCjkLost("x ''日本'' y");
    assertNoCjkLost("x '''東京''' y");
  }

  /** The usual lead sentence of an article: a bold title, then plain text. */
  public void testBoldLeadSentence() throws Exception {
    assertNoCjkLost("'''東京'''は[[日本]]の首都である。");
  }

  public void testTemplateAndCitation() throws Exception {
    assertNoCjkLost("{{日本}}");
    assertNoCjkLost("<ref>日本</ref>");
  }

  public void testExternalLinkLabel() throws Exception {
    assertNoCjkLost("[http://example.org 日本]");
  }

  /** The untokenized output modes do not recover the text either. */
  public void testUntokenizedModes() throws Exception {
    Set<String> types = Set.of(WikipediaTokenizer.INTERNAL_LINK, WikipediaTokenizer.HEADING);
    assertNoCjkLost(WikipediaTokenizer.UNTOKENIZED_ONLY, types, "[[日本]] == 歴史 ==");
    assertNoCjkLost(WikipediaTokenizer.BOTH, types, "[[日本]] == 歴史 ==");
  }
}
