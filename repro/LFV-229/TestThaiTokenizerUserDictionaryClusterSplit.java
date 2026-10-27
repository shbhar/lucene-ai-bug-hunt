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
package org.apache.lucene.analysis.th;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * A ThaiTokenizer user-dictionary word is accepted as a match even when it ends inside a
 * character cluster, i.e. directly before a combining vowel or tone mark of the text. The rest of
 * the cluster then starts with a combining mark, which ThaiTokenizer skips as a non-letter
 * segment, so characters of the word are silently dropped from the token stream.
 */
public class TestThaiTokenizerUserDictionaryClusterSplit extends BaseTokenStreamTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    assumeTrue("needs the Thai BreakIterator", ThaiTokenizer.DBBI_AVAILABLE);
  }

  private static List<String> tokens(CharArraySet dict, String text, boolean[] covered)
      throws IOException {
    Tokenizer t = dict == null ? new ThaiTokenizer() : new ThaiTokenizer(dict);
    t.setReader(new StringReader(text));
    CharTermAttribute term = t.addAttribute(CharTermAttribute.class);
    OffsetAttribute off = t.addAttribute(OffsetAttribute.class);
    List<String> out = new ArrayList<>();
    t.reset();
    while (t.incrementToken()) {
      out.add(term + "[" + off.startOffset() + "," + off.endOffset() + ")");
      for (int i = off.startOffset(); i < off.endOffset(); i++) covered[i] = true;
    }
    t.end();
    t.close();
    return out;
  }

  /** Every character of a Thai word must end up inside some token. */
  private static void assertNothingDropped(CharArraySet dict, String text) throws IOException {
    boolean[] covered = new boolean[text.length()];
    List<String> toks = tokens(dict, text, covered);
    for (int i = 0; i < text.length(); i++) {
      assertTrue(
          "character "
              + i
              + String.format(java.util.Locale.ROOT, " (U+%04X)", (int) text.charAt(i))
              + " of \""
              + text
              + "\" is in no token: "
              + toks,
          covered[i]);
    }
  }

  /** Control: without a user dictionary the word "แม่น้ำ" (river) is one token. */
  public void testControlWithoutDictionary() throws IOException {
    Tokenizer t = new ThaiTokenizer();
    t.setReader(new StringReader("แม่น้ำ"));
    assertTokenStreamContents(t, new String[] {"แม่น้ำ"}, new int[] {0}, new int[] {6});
  }

  /**
   * The user word "แม่น" (accurate) is a character prefix of "แม่น้ำ" (river) that ends before the
   * tone mark U+0E49; the tone mark and SARA AM U+0E33 are dropped.
   */
  public void testDictionaryWordEndingBeforeToneMark() throws IOException {
    assertNothingDropped(new CharArraySet(List.of("แม่น"), false), "แม่น้ำ");
  }

  /** The common word "ผล" (result) splits "ผลิต" (produce) before the vowel U+0E34. */
  public void testCommonWordPrefix() throws IOException {
    assertNothingDropped(new CharArraySet(List.of("ผล"), false), "ผลิต");
  }

  /** The user word "สม" splits "สมุด" (notebook) before the vowel U+0E38, which is dropped. */
  public void testDictionaryWordEndingBeforeVowelMark() throws IOException {
    assertNothingDropped(new CharArraySet(List.of("สม"), false), "ผมซื้อสมุดใหม่");
  }
}
