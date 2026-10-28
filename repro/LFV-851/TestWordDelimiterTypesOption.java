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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * The "types" option of WordDelimiterGraphFilterFactory (and of the deprecated
 * WordDelimiterFilterFactory) should change the type of the characters it lists and nothing else.
 * Public API only: a whitespace tokenizer plus the filter with default flags, built with
 * CustomAnalyzer with and without a types file.
 */
public class TestWordDelimiterTypesOption extends BaseTokenStreamTestCase {

  private Analyzer analyzer(String filter, String typesRule) throws IOException {
    if (typesRule == null) {
      return CustomAnalyzer.builder().withTokenizer("whitespace").addTokenFilter(filter).build();
    }
    Path dir = createTempDir();
    Files.writeString(dir.resolve("types.txt"), typesRule + "\n", StandardCharsets.UTF_8);
    return CustomAnalyzer.builder(dir)
        .withTokenizer("whitespace")
        .addTokenFilter(filter, "types", "types.txt")
        .build();
  }

  private static List<String> terms(Analyzer a, String text) throws IOException {
    List<String> out = new ArrayList<>();
    try (TokenStream ts = a.tokenStream("f", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        out.add(term.toString());
      }
      ts.end();
    }
    return out;
  }

  /** Asserts that each text gives the same terms with and without the types rule. */
  private void assertSameTerms(String filter, String typesRule, String... texts)
      throws IOException {
    try (Analyzer plain = analyzer(filter, null);
        Analyzer typed = analyzer(filter, typesRule)) {
      for (String text : texts) {
        assertEquals(
            "text '" + text + "', types rule '" + typesRule + "'",
            terms(plain, text),
            terms(typed, text));
      }
    }
  }

  /** '-' already is SUBWORD_DELIM, so this rule restates the default and must change nothing. */
  public void testNoOpRule() throws IOException {
    // without a types file: [a, b, m, 1, ª, B]
    assertSameTerms("wordDelimiterGraph", "- => SUBWORD_DELIM", "a-b m² 1½ ¼ ªB");
  }

  /** The deprecated factory has the same code. */
  @SuppressWarnings("deprecation")
  public void testNoOpRuleDeprecatedFactory() throws IOException {
    assertSameTerms("wordDelimiter", "- => SUBWORD_DELIM", "a-b m² 1½ ¼ ªB");
  }

  /** A rule for '$' must not change text that has no '$'. */
  public void testRuleForAnotherCharacter() throws IOException {
    try (Analyzer typed = analyzer("wordDelimiterGraph", "$ => DIGIT")) {
      // control: the rule itself takes effect (without it: [US, 5])
      assertEquals(List.of("US", "$5"), terms(typed, "US$5"));
    }
    // without a types file: [m, x, 1]
    assertSameTerms("wordDelimiterGraph", "$ => DIGIT", "m² x³ 1½ ¼");
  }

  /** With the no-op rule, no character below U+0100 may be tokenized differently. */
  public void testNoOpRuleChangesNoLatin1Character() throws IOException {
    List<String> changed = new ArrayList<>();
    try (Analyzer plain = analyzer("wordDelimiterGraph", null);
        Analyzer typed = analyzer("wordDelimiterGraph", "- => SUBWORD_DELIM")) {
      for (char c = 0; c < 256; c++) {
        if (c == '-' || Character.isWhitespace(c) || Character.isISOControl(c)) {
          continue;
        }
        for (String probe : new String[] {"" + c, "a" + c + "b", "A" + c + "B"}) {
          if (terms(plain, probe).equals(terms(typed, probe)) == false) {
            changed.add(String.format(Locale.ROOT, "U+%04X", (int) c));
            break;
          }
        }
      }
    }
    assertEquals(List.of(), changed);
  }
}
