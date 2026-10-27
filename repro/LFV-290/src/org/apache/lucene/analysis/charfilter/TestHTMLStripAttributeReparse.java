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
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A quoted attribute value that ends with whitespace + name + '=' (for example a search URL with
 * an unencoded space, or alt text such as "x =") lets HTMLStripCharFilter re-read the value's
 * closing quote as the opening quote of a new attribute, so the tag runs on to the next quote in
 * the document and the text in between is dropped. The case reported in GITHUB#15754 (no
 * whitespace before the name) is fixed; this variant with whitespace is not. Public API only.
 */
public class TestHTMLStripAttributeReparse extends LuceneTestCase {

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

  /** Control: the GITHUB#15754 input, fixed on main. */
  public void testGithub15754Fixed() throws Exception {
    String out =
        strip(
            "<a href=\"https://www.example.com/?test=\">example</a> this gets discarded"
                + " <a href=\"next\">example2</a>");
    assertEquals("example this gets discarded example2", out);
  }

  public void testWhitespaceNameEqualsAtEndOfValue() throws Exception {
    String out =
        strip(
            "<a href=\"/search?q=a b=\">example</a> this gets discarded"
                + " <a href=\"next\">example2</a>");
    assertEquals("example this gets discarded example2", out);
  }

  public void testAltTextEndingInEquals() throws Exception {
    String out = strip("<img alt=\"E=mc2 and x =\"> caption text <a href=\"next\">link</a>");
    assertEquals(" caption text link", out);
  }
}
