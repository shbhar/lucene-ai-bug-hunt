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
package org.apache.lucene.analysis.icu.v1;

import com.ibm.icu.text.RuleBasedBreakIterator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.WordlistLoader;
import org.apache.lucene.analysis.icu.segmentation.ICUTokenizerFactory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.util.ResourceLoader;

/**
 * Independent check: a per-script RBBI rule file for ICUTokenizerFactory ("rulefiles" argument)
 * that is saved as UTF-8 with a byte order mark should tokenize exactly like the same file saved
 * without the byte order mark. Public API only (a package outside Lucene).
 */
public class TestIcuRuleFileBom extends BaseTokenStreamTestCase {

  private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

  /** First statement is a rule (the shape of a keyword-style rule file). */
  private static final String KEYWORD_RULES = ".+ {200};\n";

  /** Starts with a comment line, then a '!!' directive (the shape of a typical rule file). */
  private static final String COMMENT_FIRST_RULES =
      "# Break only on whitespace\n"
          + "!!forward;\n"
          + "$Ws = [\\p{Whitespace}];\n"
          + "$NonWs = [\\P{Whitespace}];\n"
          + "$Ws;\n"
          + "$NonWs+ {200};\n";

  /** First statement is a variable definition. */
  private static final String VARIABLE_FIRST_RULES =
      "$Ws = [\\p{Whitespace}];\n" + "$NonWs = [\\P{Whitespace}];\n" + "$Ws;\n" + "$NonWs+ {200};\n";

  /** A comment header (e.g. a license header), then a rule as the first statement. */
  private static final String HEADER_THEN_RULE =
      "# Keyword rules: the whole run is one token\n" + "#\n" + "\n" + ".+ {200};\n";

  /** In-memory resource loader serving raw bytes, as a file on disk would. */
  private static final class BytesLoader implements ResourceLoader {
    private final Map<String, byte[]> files = new HashMap<>();

    BytesLoader put(String name, byte[] bytes) {
      files.put(name, bytes);
      return this;
    }

    @Override
    public InputStream openResource(String resource) throws IOException {
      byte[] b = files.get(resource);
      if (b == null) {
        throw new IOException("no such resource: " + resource);
      }
      return new ByteArrayInputStream(b);
    }

    @Override
    public <T> Class<? extends T> findClass(String cname, Class<T> expectedType) {
      throw new UnsupportedOperationException();
    }
  }

  private static byte[] utf8(String s, boolean bom) {
    byte[] body = s.getBytes(StandardCharsets.UTF_8);
    if (!bom) {
      return body;
    }
    byte[] out = new byte[UTF8_BOM.length + body.length];
    System.arraycopy(UTF8_BOM, 0, out, 0, UTF8_BOM.length);
    System.arraycopy(body, 0, out, UTF8_BOM.length, body.length);
    return out;
  }

  private Tokenizer latinTokenizer(String rules, boolean bom) throws IOException {
    Map<String, String> args = new HashMap<>();
    args.put("rulefiles", "Latn:latin.rbbi");
    ICUTokenizerFactory factory = new ICUTokenizerFactory(args);
    factory.inform(new BytesLoader().put("latin.rbbi", utf8(rules, bom)));
    Tokenizer t = factory.create(newAttributeFactory());
    t.setReader(new StringReader("ab cd"));
    return t;
  }

  // ---- controls: same rules without a BOM ----

  public void testKeywordRulesNoBom() throws Exception {
    assertTokenStreamContents(latinTokenizer(KEYWORD_RULES, false), new String[] {"ab cd"});
  }

  public void testCommentFirstRulesNoBom() throws Exception {
    assertTokenStreamContents(latinTokenizer(COMMENT_FIRST_RULES, false), new String[] {"ab", "cd"});
  }

  public void testVariableFirstRulesNoBom() throws Exception {
    assertTokenStreamContents(latinTokenizer(VARIABLE_FIRST_RULES, false), new String[] {"ab", "cd"});
  }

  public void testHeaderThenRuleNoBom() throws Exception {
    assertTokenStreamContents(latinTokenizer(HEADER_THEN_RULE, false), new String[] {"ab cd"});
  }

  // ---- the claim: same rules saved as UTF-8 with a BOM ----

  public void testKeywordRulesWithBom() throws Exception {
    assertTokenStreamContents(latinTokenizer(KEYWORD_RULES, true), new String[] {"ab cd"});
  }

  public void testCommentFirstRulesWithBom() throws Exception {
    assertTokenStreamContents(latinTokenizer(COMMENT_FIRST_RULES, true), new String[] {"ab", "cd"});
  }

  public void testVariableFirstRulesWithBom() throws Exception {
    assertTokenStreamContents(latinTokenizer(VARIABLE_FIRST_RULES, true), new String[] {"ab", "cd"});
  }

  public void testHeaderThenRuleWithBom() throws Exception {
    assertTokenStreamContents(latinTokenizer(HEADER_THEN_RULE, true), new String[] {"ab cd"});
  }

  // ---- mechanism / convention controls (expected to pass) ----

  /**
   * ICU4J's String constructor treats a leading U+FEFF as a literal character of the first rule:
   * it compiles, but the rule no longer matches "ab cd", so the status is 0 (not 200).
   */
  public void testIcuStringApiTreatsLeadingFeffAsLiteral() throws Exception {
    RuleBasedBreakIterator plain = new RuleBasedBreakIterator(KEYWORD_RULES);
    plain.setText("ab cd");
    assertEquals(5, plain.next());
    assertEquals(200, plain.getRuleStatus());

    RuleBasedBreakIterator withFeff = new RuleBasedBreakIterator("\uFEFF" + KEYWORD_RULES);
    withFeff.setText("ab cd");
    withFeff.next();
    assertEquals(0, withFeff.getRuleStatus());
  }

  /** Lucene's own resource line loader drops a leading BOM (convention for resource files). */
  public void testWordlistLoaderGetLinesSkipsBom() throws Exception {
    List<String> lines =
        WordlistLoader.getLines(
            new ByteArrayInputStream(utf8("foo\nbar\n", true)), StandardCharsets.UTF_8);
    assertEquals(List.of("foo", "bar"), lines);
  }
}
