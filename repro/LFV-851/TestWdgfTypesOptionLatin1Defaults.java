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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * WordDelimiterGraphFilterFactory's "types" option is meant to override the character types of the
 * listed characters only. It builds its table by calling WordDelimiterIterator.getType(i) for every
 * i, but the table the filter uses without the option (DEFAULT_WORD_DELIM_TABLE) is computed
 * differently for U+0000-U+00FF. The two disagree on 8 Latin-1 characters, so a rule for an unrelated
 * character (here the factory's own example "$ => DIGIT") changes how SUPERSCRIPT TWO/THREE/ONE and
 * the vulgar fractions are tokenized. Uses public API only.
 */
public class TestWdgfTypesOptionLatin1Defaults extends BaseTokenStreamTestCase {

  private static List<String> terms(Analyzer a, String text) throws Exception {
    List<String> out = new ArrayList<>();
    try (TokenStream ts = a.tokenStream("f", text)) {
      CharTermAttribute t = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) out.add(t.toString());
      ts.end();
    }
    return out;
  }

  /** Control: the table without the option and getType agree on every other char below 256. Passes. */
  public void testDefaultTableAgreesWithGetTypeOutsideTheEightChars() {
    int disagreements = 0;
    for (int c = 0; c < WordDelimiterIterator.DEFAULT_WORD_DELIM_TABLE.length; c++) {
      if (WordDelimiterIterator.DEFAULT_WORD_DELIM_TABLE[c] != WordDelimiterIterator.getType(c)) {
        disagreements++;
      }
    }
    // U+00AA, U+00B2, U+00B3, U+00B9, U+00BA, U+00BC, U+00BD, U+00BE
    assertEquals(8, disagreements);
  }

  /** Fails on main: a "$" rule changes the tokens of text that contains no "$". */
  public void testUnrelatedTypesRuleDoesNotChangeOtherCharacters() throws Exception {
    Path dir = createTempDir();
    Files.writeString(dir.resolve("types.txt"), "$ => DIGIT\n", StandardCharsets.UTF_8);
    String text = "m² x³ 1½ ¼";
    try (Analyzer defaults =
            CustomAnalyzer.builder()
                .withTokenizer("whitespace")
                .addTokenFilter("wordDelimiterGraph")
                .build();
        Analyzer withTypes =
            CustomAnalyzer.builder(dir)
                .withTokenizer("whitespace")
                .addTokenFilter("wordDelimiterGraph", "types", "types.txt")
                .build()) {
      // control: the rule itself works ("$" is no longer a delimiter)
      assertEquals(List.of("US", "5"), terms(defaults, "US$5"));
      assertEquals(List.of("US", "$5"), terms(withTypes, "US$5"));
      // text without "$": should analyze identically with and without the rule
      assertEquals(terms(defaults, text), terms(withTypes, text));
    }
  }
}
