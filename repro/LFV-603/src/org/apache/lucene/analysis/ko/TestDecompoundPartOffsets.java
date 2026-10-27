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
package org.apache.lucene.analysis.ko;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.ko.KoreanTokenizer.DecompoundMode;
import org.apache.lucene.analysis.ko.dict.UserDictionary;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * A decompounded part of a compound word is cut from the word's text from the left
 * (UserMorphData/TokenInfoMorphData.getMorphemes), but KoreanTokenizer computes its offsets from
 * the right end of the word (Viterbi backtrace). When the parts do not cover the whole word, each
 * part's offsets point at other characters than the part's own text.
 *
 * <p>Uses only public API (KoreanTokenizer, UserDictionary.open).
 */
public class TestDecompoundPartOffsets extends BaseTokenStreamTestCase {

  /** Each emitted token's offsets must slice the source text it was cut from. */
  private static List<String> mismatches(Tokenizer t, String text) throws IOException {
    List<String> bad = new ArrayList<>();
    CharTermAttribute term = t.addAttribute(CharTermAttribute.class);
    OffsetAttribute off = t.addAttribute(OffsetAttribute.class);
    t.setReader(new StringReader(text));
    t.reset();
    while (t.incrementToken()) {
      String slice = text.substring(off.startOffset(), off.endOffset());
      if (slice.equals(term.toString()) == false) {
        bad.add(term + " [" + off.startOffset() + "," + off.endOffset() + ") slices '" + slice + "'");
      }
    }
    t.end();
    t.close();
    return bad;
  }

  /** A user dictionary compound whose segmentation covers only the start of the word. */
  public void testUserDictionarySegmentationShorterThanWord() throws IOException {
    UserDictionary dict;
    try {
      dict = UserDictionary.open(new StringReader("세종시 세종\n"));
    } catch (IllegalArgumentException rejected) {
      return; // rejecting the entry (as the Japanese UserDictionary does) is also correct
    }
    for (DecompoundMode mode : new DecompoundMode[] {DecompoundMode.DISCARD, DecompoundMode.MIXED}) {
      Tokenizer t = new KoreanTokenizer(newAttributeFactory(), dict, mode, false);
      List<String> bad = mismatches(t, "세종시");
      assertTrue(mode + ": token offsets do not match the token text: " + bad, bad.isEmpty());
    }
  }

  /** A compound of the bundled system dictionary whose expression does not cover the word. */
  public void testSystemDictionaryCompound() throws IOException {
    for (String word : new String[] {"픽토그램", "녹색시민연합", "테크노파크역"}) {
      Tokenizer t = new KoreanTokenizer(); // default: DecompoundMode.DISCARD, as in KoreanAnalyzer
      List<String> bad = mismatches(t, word);
      assertTrue(word + ": token offsets do not match the token text: " + bad, bad.isEmpty());
    }
  }
}
