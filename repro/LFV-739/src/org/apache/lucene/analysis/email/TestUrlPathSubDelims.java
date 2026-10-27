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
package org.apache.lucene.analysis.email;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * URL path segments stop at the characters RFC 1738 calls "safe"/"extra" and RFC 3986 calls
 * sub-delims ($ + ! * ' ( ) ,), although both RFCs the grammar cites allow them in http, ftp and
 * file paths. The query and fragment rules already accept them.
 */
public class TestUrlPathSubDelims extends BaseTokenStreamTestCase {

  private static Analyzer tokenizerOnly() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        return new TokenStreamComponents(new UAX29URLEmailTokenizer(newAttributeFactory()));
      }
    };
  }

  private static void assertOneUrl(Analyzer a, String url) throws Exception {
    assertAnalyzesTo(
        a,
        "see " + url + " now",
        new String[] {"see", url, "now"},
        new String[] {"<ALPHANUM>", "<URL>", "<ALPHANUM>"});
  }

  /** Control: the same characters in the query part are accepted. */
  public void testControlQuery() throws Exception {
    try (Analyzer a = tokenizerOnly()) {
      assertOneUrl(a, "http://example.com/a?b,c(d)+e");
    }
  }

  public void testHttpPathParentheses() throws Exception {
    try (Analyzer a = tokenizerOnly()) {
      assertOneUrl(a, "http://en.wikipedia.org/wiki/Python_(programming_language)");
    }
  }

  public void testHttpPathCommaAndPlus() throws Exception {
    try (Analyzer a = tokenizerOnly()) {
      assertOneUrl(a, "http://example.com/a,b");
      assertOneUrl(a, "http://example.com/search/new+york");
    }
  }

  public void testNoSchemePath() throws Exception {
    try (Analyzer a = tokenizerOnly()) {
      assertOneUrl(a, "example.com/wiki/Foo_(bar)");
    }
  }

  public void testFtpPath() throws Exception {
    try (Analyzer a = tokenizerOnly()) {
      assertOneUrl(a, "ftp://ftp.example.com/pub/notes(v2).txt");
    }
  }
}
