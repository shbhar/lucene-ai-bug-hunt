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
package org.apache.lucene.analysis.charfilter;

import java.io.Reader;
import java.io.StringReader;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.analysis.MockTokenizer;

/**
 * An apostrophe inside a JavaScript or CSS comment in a script/style block that is wrapped in
 * {@code <!-- -->} (a common legacy idiom) makes HTMLStripCharFilter drop all of the following
 * document text. Only the public API is used.
 */
public class TestHTMLStripScriptCommentQuote extends BaseTokenStreamTestCase {

  private static String strip(String html) throws Exception {
    try (Reader r = new HTMLStripCharFilter(new StringReader(html))) {
      StringBuilder sb = new StringBuilder();
      char[] buf = new char[256];
      int n;
      while ((n = r.read(buf, 0, buf.length)) != -1) {
        sb.append(buf, 0, n);
      }
      return sb.toString();
    }
  }

  private static Analyzer htmlAnalyzer() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer t = new MockTokenizer(MockTokenizer.WHITESPACE, false);
        return new TokenStreamComponents(t, t);
      }

      @Override
      protected Reader initReader(String fieldName, Reader reader) {
        return new HTMLStripCharFilter(reader);
      }
    };
  }

  /** Control: without the apostrophe the text after the script is kept. */
  public void testControlNoApostrophe() throws Exception {
    String html =
        "<script type=\"text/javascript\"><!--\n// Do not remove\nvar x = 1;\n//--></script>"
            + "<p>Welcome to our store</p>";
    assertTrue(strip(html), strip(html).contains("Welcome to our store"));
  }

  public void testApostropheInScriptComment() throws Exception {
    String html =
        "<script type=\"text/javascript\"><!--\n// Don't remove\nvar x = 1;\n//--></script>"
            + "<p>Welcome to our store</p>";
    String out = strip(html);
    assertTrue("text after </script> was dropped, output=[" + out + "]",
        out.contains("Welcome to our store"));
  }

  public void testApostropheInStyleComment() throws Exception {
    String html =
        "<style type=\"text/css\"><!--\n/* Outlook's spacing fix */\np { margin: 0; }\n--></style>"
            + "<p>Your order has shipped.</p>";
    String out = strip(html);
    assertTrue("text after </style> was dropped, output=[" + out + "]",
        out.contains("Your order has shipped."));
  }

  /** The same through an Analyzer: the field indexes no tokens at all. */
  public void testAnalyzerIndexesNothing() throws Exception {
    try (Analyzer a = htmlAnalyzer()) {
      assertAnalyzesTo(
          a,
          "<style><!-- /* Outlook's fix */ --></style><p>Your order has shipped</p>",
          new String[] {"Your", "order", "has", "shipped"});
    }
  }
}
