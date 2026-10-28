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

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.util.StringMockResourceLoader;

/**
 * An ICUTokenizerFactory per-script rule file saved as UTF-8 with a byte order mark (as some
 * editors do) is accepted without an error, but the BOM becomes a literal character of the first
 * rule, so every token of that script disappears.
 *
 * <p>Public API only: ICUTokenizerFactory(Map), inform(ResourceLoader), create(AttributeFactory).
 */
public class TestICUTokenizerFactoryRuleFileBom extends BaseTokenStreamTestCase {

  private static final String BOM = "\uFEFF";

  /** The rules of the upstream test file Latin-break-only-on-whitespace.rbbi (with a comment). */
  private static final String WHITESPACE_RULES =
      "# Latin: break only on whitespace\n"
          + "!!forward;\n"
          + "$Whitespace = [\\p{Whitespace}];\n"
          + "$NonWhitespace = [\\P{Whitespace}];\n"
          + "$Letter = [\\p{Letter}];\n"
          + "$Number = [\\p{Number}];\n"
          + "$Whitespace;\n"
          + "$NonWhitespace* $Letter $NonWhitespace*   {200};\n"
          + "$NonWhitespace* $Number $NonWhitespace*   {100};\n"
          + "$NonWhitespace+   {1};\n";

  private static final String KEYWORD_RULES = ".+ {200};\n";

  private Tokenizer latin(String ruleFile) throws Exception {
    Map<String, String> args = new HashMap<>();
    args.put("rulefiles", "Latn:latin.rbbi");
    ICUTokenizerFactory factory = new ICUTokenizerFactory(args);
    factory.inform(new StringMockResourceLoader(ruleFile));
    return factory.create(newAttributeFactory());
  }

  private void check(String ruleFile, String text, String... expected) throws Exception {
    Tokenizer t = latin(ruleFile);
    t.setReader(new StringReader(text));
    assertTokenStreamContents(t, expected);
  }

  /** Control: the files without a BOM. */
  public void testNoBom() throws Exception {
    check(WHITESPACE_RULES, "Don't,break.at?/(punct)! here", "Don't,break.at?/(punct)!", "here");
    check(KEYWORD_RULES, "ab cd", "ab cd");
  }

  /** The same whitespace rules file saved with a BOM. */
  public void testWhitespaceRulesWithBom() throws Exception {
    check(
        BOM + WHITESPACE_RULES, "Don't,break.at?/(punct)! here", "Don't,break.at?/(punct)!", "here");
  }

  /** The same keyword rules file saved with a BOM. */
  public void testKeywordRulesWithBom() throws Exception {
    check(BOM + KEYWORD_RULES, "ab cd", "ab cd");
  }
}
