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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestWdgfTypesRuleKeepsOtherTypes extends LuceneTestCase {

  private static List<String> terms(Analyzer a, String text) throws Exception {
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

  public void testNoOpRuleKeepsSuperscriptTwo() throws Exception {
    Path dir = createTempDir();
    Files.writeString(dir.resolve("types.txt"), "- => SUBWORD_DELIM\n");
    try (Analyzer plain =
            CustomAnalyzer.builder()
                .withTokenizer("whitespace")
                .addTokenFilter("wordDelimiterGraph")
                .build();
        Analyzer typed =
            CustomAnalyzer.builder(dir)
                .withTokenizer("whitespace")
                .addTokenFilter("wordDelimiterGraph", "types", "types.txt")
                .build()) {
      // '-' already is SUBWORD_DELIM; expected [m] as without the file, actual [m, ²]
      assertEquals(terms(plain, "m²"), terms(typed, "m²"));
    }
  }
}
