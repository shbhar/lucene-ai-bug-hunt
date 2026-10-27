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
package org.apache.lucene.analysis.ja;

import java.io.IOException;
import java.io.StringReader;
import org.apache.lucene.analysis.ja.dict.UserDictionary;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * UserDictionary validates an entry by comparing the surface form and the concatenated
 * segmentation after removing all whitespace from both (LUCENE-8933). The segment lengths,
 * however, come from splitting the segmentation on ' ' only, and the dictionary key is the surface
 * form with its whitespace. An entry with a tab between segments, a leading space before the
 * segmentation, or whitespace inside the surface form is therefore accepted although its segment
 * lengths do not add up to the matched text, and JapaneseTokenizer then throws, emits an empty
 * token, or emits wrong tokens and drops characters.
 *
 * <p>Oracle: an entry is either rejected when the dictionary is loaded, or the tokens it produces
 * are non-empty, equal the input at their offsets and (NORMAL mode, punctuation kept) cover the
 * input exactly once, in order.
 */
public class TestUserDictionaryWhitespaceSegments extends LuceneTestCase {

  private static void assertRejectedOrCoherent(String entry, String text) throws IOException {
    final UserDictionary dict;
    try {
      dict = UserDictionary.open(new StringReader(entry));
    } catch (RuntimeException rejected) {
      return; // rejected at load time: fine
    }
    assertNotNull(dict);
    StringBuilder covered = new StringBuilder();
    StringBuilder trace = new StringBuilder();
    try (JapaneseTokenizer tok =
        new JapaneseTokenizer(dict, false, JapaneseTokenizer.Mode.NORMAL)) {
      CharTermAttribute term = tok.addAttribute(CharTermAttribute.class);
      OffsetAttribute off = tok.addAttribute(OffsetAttribute.class);
      tok.setReader(new StringReader(text));
      tok.reset();
      int lastEnd = 0;
      while (tok.incrementToken()) {
        int s = off.startOffset(), e = off.endOffset();
        trace.append('[').append(term).append(' ').append(s).append('-').append(e).append("] ");
        assertTrue("empty token " + trace + " for entry " + entry, e > s);
        assertEquals(
            "token text vs input at its offsets " + trace, text.substring(s, e), term.toString());
        assertEquals("gap or overlap before token " + trace + " for entry " + entry, lastEnd, s);
        covered.append(term);
        lastEnd = e;
      }
      tok.end();
    }
    assertEquals("tokens " + trace + "for entry " + entry, text, covered.toString());
  }

  /** A tab between two segments: accepted, then incrementToken throws. */
  public void testTabBetweenSegments() throws Exception {
    assertRejectedOrCoherent(
        "日本経済新聞,日本\t経済 新聞,ニホン\tケイザイ シンブン,カスタム名詞", "日本経済新聞");
  }

  /** A space after the comma (", 日本 経済 新聞"): accepted, then a zero-length token. */
  public void testLeadingSpaceBeforeSegmentation() throws Exception {
    assertRejectedOrCoherent(
        "日本経済新聞, 日本 経済 新聞, ニホン ケイザイ シンブン,カスタム名詞", "日本経済新聞");
  }

  /** A space inside the surface form: accepted, then wrong tokens and a lost character. */
  public void testSpaceInsideSurfaceForm() throws Exception {
    assertRejectedOrCoherent(
        "ソニー ミュージック,ソニー ミュージック,ソニー ミュージック,カスタム名詞", "ソニー ミュージック");
  }
}
