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
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.lucene.analysis.TokenFilterFactory;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.util.StringMockResourceLoader;
import org.apache.lucene.util.ResourceLoaderAware;

/**
 * A "types" mapping for one character should only change how that character is treated. Every
 * other character should be tokenized as it is without the option.
 *
 * <p>Uses only the public factory API (Map args + inform + create).
 */
public class TestTypesRuleKeepsOtherCharTypes extends BaseTokenStreamTestCase {

  private static List<String> terms(TokenFilterFactory factory, String text) throws IOException {
    WhitespaceTokenizer tokenizer = new WhitespaceTokenizer();
    tokenizer.setReader(new StringReader(text));
    List<String> out = new ArrayList<>();
    try (TokenStream ts = factory.create(tokenizer)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        out.add(term.toString());
      }
      ts.end();
    }
    return out;
  }

  @SuppressWarnings("deprecation")
  private static TokenFilterFactory factory(boolean graph, String typesFileContent)
      throws IOException {
    Map<String, String> args = new HashMap<>();
    if (typesFileContent != null) {
      args.put("types", "types.txt");
    }
    TokenFilterFactory f =
        graph ? new WordDelimiterGraphFilterFactory(args) : new WordDelimiterFilterFactory(args);
    ((ResourceLoaderAware) f)
        .inform(new StringMockResourceLoader(typesFileContent == null ? "" : typesFileContent));
    return f;
  }

  /** Probes for character c; each places c next to lower, upper and digit neighbours. */
  private static String[] probes(char c) {
    return new String[] {"" + c, "a" + c + "b", "A" + c + "B", c + "B", "1" + c + "2", "x" + c};
  }

  /**
   * Returns a description of every character below U+0100 (other than ruleChar and whitespace)
   * whose tokenization changes when the types file is present.
   */
  private static List<String> changedChars(boolean graph, String typesFile, char ruleChar)
      throws IOException {
    TokenFilterFactory plain = factory(graph, null);
    TokenFilterFactory withRule = factory(graph, typesFile);
    List<String> changed = new ArrayList<>();
    for (int i = 0; i < 256; i++) {
      char c = (char) i;
      if (c == ruleChar || Character.isWhitespace(c) || Character.isISOControl(c)) {
        continue;
      }
      for (String p : probes(c)) {
        List<String> a = terms(plain, p);
        List<String> b = terms(withRule, p);
        if (!a.equals(b)) {
          changed.add(String.format("U+%04X '%s': without=%s with=%s", i, p, a, b));
          break;
        }
      }
    }
    return changed;
  }

  /** Sanity control: the rule itself takes effect, so the option is actually wired in. */
  public void testRuleItselfTakesEffect() throws IOException {
    assertEquals(List.of("US", "5"), terms(factory(true, null), "US$5"));
    assertEquals(List.of("US", "$5"), terms(factory(true, "$ => DIGIT"), "US$5"));
  }

  /** The concrete example: an unrelated rule for '$' changes how "m² x³ 1½ ¼" is tokenized. */
  public void testUnrelatedRuleGraph() throws IOException {
    String text = "m² x³ 1½ ¼ NºA ªB";
    assertEquals(terms(factory(true, null), text), terms(factory(true, "$ => DIGIT"), text));
  }

  /** A rule that restates the default type of '-' (a no-op) still changes other characters. */
  public void testNoOpRuleGraphExhaustiveLatin1() throws IOException {
    List<String> changed = changedChars(true, "- => SUBWORD_DELIM", '-');
    assertTrue("characters whose tokenization changed: " + changed, changed.isEmpty());
  }

  /** Same check for the older (deprecated) WordDelimiterFilterFactory. */
  public void testNoOpRuleWdfExhaustiveLatin1() throws IOException {
    List<String> changed = changedChars(false, "- => SUBWORD_DELIM", '-');
    assertTrue("characters whose tokenization changed: " + changed, changed.isEmpty());
  }
}
