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

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * HTMLStripCharFilter matches its SCRIPT, STYLE and BR start-tag rules on a prefix of the tag
 * name, so an ordinary element whose name merely starts with "script", "style" or "br" (an XML
 * element such as scripts or stylesheet, a custom element such as style-guide) is handled as a
 * script/style block or a BR tag.
 */
public class TestHTMLStripTagNamePrefix extends BaseTokenStreamTestCase {

  private static String strip(String html, Set<String> escapedTags) throws IOException {
    Reader in = new StringReader(html);
    HTMLStripCharFilter f =
        escapedTags == null ? new HTMLStripCharFilter(in) : new HTMLStripCharFilter(in, escapedTags);
    StringBuilder sb = new StringBuilder();
    int ch;
    while ((ch = f.read()) != -1) {
      sb.append((char) ch);
    }
    f.close();
    return sb.toString();
  }

  /** An element named "scripts" must be treated like any other unknown element. */
  public void testElementNameStartingWithScriptKeepsText() throws Exception {
    // control: same shape, a name that does not start with "script"
    assertEquals("\nhello\n world", strip("<scrapes>hello</scrapes> world", null));
    // the content and everything after it are dropped
    assertEquals("\nhello\n world", strip("<scripts>hello</scripts> world", null));
  }

  /** A custom element whose name starts with "style-". */
  public void testCustomElementStartingWithStyleKeepsText() throws Exception {
    assertEquals("\nhello\n world", strip("<stale-guide>hello</stale-guide> world", null));
    assertEquals("\nhello\n world", strip("<style-guide>hello</style-guide> world", null));
  }

  /** The analysis chain indexes nothing for the document. */
  public void testAnalyzerIndexesText() throws Exception {
    Analyzer a =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer t = new StandardTokenizer();
            return new TokenStreamComponents(t);
          }

          @Override
          protected Reader initReader(String fieldName, Reader reader) {
            return new HTMLStripCharFilter(reader);
          }
        };
    assertAnalyzesTo(
        a,
        "<stylesheet>hello</stylesheet> world",
        new String[] {"hello", "world"},
        new int[] {12, 31},
        new int[] {17, 36});
    a.close();
  }

  /** escapedTags: "Tags in this set (both start and end tags) will not be filtered out." */
  public void testEscapedTagStartingWithBrOrScript() throws Exception {
    assertEquals("<bland>Acme</bland>", strip("<bland>Acme</bland>", Set.of("bland")));
    assertEquals("<brand>Acme</brand>", strip("<brand>Acme</brand>", Set.of("brand")));
    assertEquals(
        "<scripts>hello</scripts> world", strip("<scripts>hello</scripts> world", Set.of("scripts")));
  }
}
