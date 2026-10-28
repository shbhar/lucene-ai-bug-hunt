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
package org.apache.lucene.analysis.miscellaneous;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.tokenattributes.TermToBytesRefAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * ConcatenateGraphFilter handles a custom tokenSeparator (LUCENE-9018) as if it were the internal one-byte SEP_LABEL:
 *
 * <ul>
 *   <li>the SEP_LABEL escape (EscapingTokenStreamToAutomaton) doubles every occurrence of the custom separator
 *       character inside a token, and the doubled character is emitted verbatim;
 *   <li>the separator is written as one byte label, so a separator outside ASCII trips an assert (-ea) or is emitted
 *       as one truncated byte (-da): invalid UTF-8, or a different character.
 * </ul>
 */
public class TestConcatenateGraphCustomSeparator extends LuceneTestCase {

  private static List<String> concat(TokenStream in, Character sep) throws Exception {
    List<String> out = new ArrayList<>();
    try (ConcatenateGraphFilter f = new ConcatenateGraphFilter(in, sep, false, 100)) {
      TermToBytesRefAttribute att = f.addAttribute(TermToBytesRefAttribute.class);
      f.reset();
      while (f.incrementToken()) {
        out.add(att.getBytesRef().utf8ToString());
      }
      f.end();
    }
    return out;
  }

  /** Joining a single token yields the token itself. */
  public void testSingleTokenContainingTheSeparator() throws Exception {
    Tokenizer t = new KeywordTokenizer();
    t.setReader(new StringReader("e-mail"));
    assertEquals(List.of("e-mail"), concat(t, '-'));
  }

  /** "Concatenates/Joins every incoming token with a separator": join(["e-mail", "x"], "-"). */
  public void testTwoTokensFirstContainsTheSeparator() throws Exception {
    Tokenizer t = new WhitespaceTokenizer();
    t.setReader(new StringReader("e-mail x"));
    assertEquals(List.of("e-mail-x"), concat(t, '-'));
  }

  /** Same through the SPI factory parameter "tokenSeparator", with a space separator. */
  public void testFactoryWithSpaceSeparator() throws Exception {
    Analyzer a =
        CustomAnalyzer.builder()
            .withTokenizer("keyword")
            .addTokenFilter("concatenateGraph", "tokenSeparator", " ")
            .build();
    List<String> out = new ArrayList<>();
    try (TokenStream ts = a.tokenStream("f", "new york")) {
      TermToBytesRefAttribute att = ts.addAttribute(TermToBytesRefAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        out.add(att.getBytesRef().utf8ToString());
      }
      ts.end();
    }
    assertEquals(List.of("new york"), out);
  }

  private static List<BytesRef> concatBytes(TokenStream in, Character sep) throws Exception {
    List<BytesRef> out = new ArrayList<>();
    try (ConcatenateGraphFilter f = new ConcatenateGraphFilter(in, sep, false, 100)) {
      TermToBytesRefAttribute att = f.addAttribute(TermToBytesRefAttribute.class);
      f.reset();
      while (f.incrementToken()) {
        out.add(BytesRef.deepCopyOf(att.getBytesRef()));
      }
      f.end();
    }
    return out;
  }

  /** U+00B7 MIDDLE DOT: expected the UTF-8 bytes of "new·york". */
  public void testLatin1Separator() throws Exception {
    Tokenizer t = new WhitespaceTokenizer();
    t.setReader(new StringReader("new york"));
    assertEquals(List.of(new BytesRef("new·york")), concatBytes(t, '·'));
  }

  /** U+2013 EN DASH: expected "new–york". */
  public void testBmpSeparator() throws Exception {
    Tokenizer t = new WhitespaceTokenizer();
    t.setReader(new StringReader("new york"));
    assertEquals(List.of(new BytesRef("new–york")), concatBytes(t, '–'));
  }

  /** Same through the SPI factory ("tokenSeparator" accepts any single char). */
  public void testFactory() throws Exception {
    Analyzer a =
        CustomAnalyzer.builder()
            .withTokenizer("whitespace")
            .addTokenFilter("concatenateGraph", "tokenSeparator", "–")
            .build();
    List<BytesRef> out = new ArrayList<>();
    try (TokenStream ts = a.tokenStream("f", "new york")) {
      TermToBytesRefAttribute att = ts.addAttribute(TermToBytesRefAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        out.add(BytesRef.deepCopyOf(att.getBytesRef()));
      }
      ts.end();
    }
    assertEquals(List.of(new BytesRef("new–york")), out);
  }
}
