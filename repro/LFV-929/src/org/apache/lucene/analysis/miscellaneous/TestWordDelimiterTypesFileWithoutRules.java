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
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.util.StringMockResourceLoader;
import org.apache.lucene.util.ResourceLoaderAware;

/**
 * The "types" option of WordDelimiterGraphFilterFactory (and of the deprecated
 * WordDelimiterFilterFactory) reads its file with getLines, whose documented format skips comment
 * lines (lines starting with "#") and blank lines. A types file that therefore holds no rule at all
 * (only comments, or empty) should behave like a file whose rules do not apply to the text. Instead
 * inform() throws java.util.NoSuchElementException (no message) from parseTypes, because it calls
 * TreeMap.lastKey() on the empty rule map.
 */
public class TestWordDelimiterTypesFileWithoutRules extends BaseTokenStreamTestCase {

  private static final String TEXT = "Wi-Fi PowerShot500 e-mail";
  private static final String COMMENT_ONLY = "# custom character types for this field\n# none yet\n";

  private static List<String> analyze(String spiName, String typesFile) throws IOException {
    Map<String, String> args = new HashMap<>();
    if (typesFile != null) {
      args.put("types", "wdfftypes.txt");
    }
    TokenFilterFactory factory = TokenFilterFactory.forName(spiName, args);
    if (typesFile != null) {
      ((ResourceLoaderAware) factory).inform(new StringMockResourceLoader(typesFile));
    }
    Tokenizer tokenizer = new WhitespaceTokenizer();
    tokenizer.setReader(new StringReader(TEXT));
    List<String> terms = new ArrayList<>();
    try (TokenStream ts = factory.create(tokenizer)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        terms.add(term.toString());
      }
      ts.end();
    }
    return terms;
  }

  /** Control: a types file with one rule for a character absent from the text loads fine. */
  public void testOneRuleControl() throws Exception {
    assertEquals(analyze("wordDelimiterGraph", null), analyze("wordDelimiterGraph", "$ => DIGIT\n"));
    assertEquals(analyze("wordDelimiter", null), analyze("wordDelimiter", "$ => DIGIT\n"));
  }

  public void testCommentOnlyTypesFileGraph() throws Exception {
    assertEquals(analyze("wordDelimiterGraph", null), analyze("wordDelimiterGraph", COMMENT_ONLY));
  }

  public void testEmptyTypesFileGraph() throws Exception {
    assertEquals(analyze("wordDelimiterGraph", null), analyze("wordDelimiterGraph", ""));
  }

  public void testCommentOnlyTypesFileDeprecatedFactory() throws Exception {
    assertEquals(analyze("wordDelimiter", null), analyze("wordDelimiter", COMMENT_ONLY));
  }
}
