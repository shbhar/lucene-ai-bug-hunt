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
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.util.UnicodeUtil;

/**
 * An unknown word ends after 1024 UTF-16 chars (MAX_UNKNOWN_WORD_LENGTH). That cut must not fall
 * between the two chars of a supplementary character.
 */
public class TestKoreanUnknownWordCap extends BaseTokenStreamTestCase {

  /** U+1F441 U+FE0F (eye + variation selector 16): one emoji of 3 UTF-16 chars. */
  private static final String EYE = "\uD83D\uDC41\uFE0F";

  private static void addBroken(TokenStream ts, String label, List<String> broken)
      throws IOException {
    CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
    OffsetAttribute offset = ts.addAttribute(OffsetAttribute.class);
    ts.reset();
    while (ts.incrementToken()) {
      if (UnicodeUtil.validUTF16String(term) == false) {
        broken.add(
            label
                + ": term ["
                + offset.startOffset()
                + ","
                + offset.endOffset()
                + "), length "
                + term.length()
                + " NOT VALID UTF-16");
      }
    }
    ts.end();
    ts.close();
  }

  /** 400 emoji (1200 chars); 0, 1 or 2 leading U+FE0F move the cut to each char of an emoji. */
  public void testDefaultAnalyzerDoesNotSplitSurrogatePair() throws IOException {
    List<String> broken = new ArrayList<>();
    try (Analyzer a = new KoreanAnalyzer()) {
      for (int shift = 0; shift < 3; shift++) {
        String text = "\uFE0F".repeat(shift) + EYE.repeat(400);
        addBroken(a.tokenStream("f", text), "KoreanAnalyzer, shift " + shift, broken);
      }
    }
    assertTrue(String.join("\n", broken), broken.isEmpty());
  }

  public void testUnknownUnigramsDoNotSplitSurrogatePair() throws IOException {
    List<String> broken = new ArrayList<>();
    Tokenizer t =
        new KoreanTokenizer(newAttributeFactory(), null, KoreanTokenizer.DEFAULT_DECOMPOUND, true);
    t.setReader(new StringReader(EYE.repeat(400)));
    addBroken(t, "KoreanTokenizer, outputUnknownUnigrams=true", broken);
    assertTrue(String.join("\n", broken), broken.isEmpty());
  }
}
