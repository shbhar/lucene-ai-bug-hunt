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
 * A double- or single-quoted attribute value whose last character is a backslash (for example a
 * Windows directory path) makes HTMLStripCharFilter read the backslash and the closing quote as an
 * escaped quote, so the "attribute value" runs on to the next quote character in the document and
 * the text in between is dropped. In HTML a backslash has no special meaning inside an attribute
 * value. Only the public API is used.
 */
public class TestHTMLStripAttributeBackslash extends LuceneTestCase {

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

  /** Control: the same document without the trailing backslash. */
  public void testControlNoTrailingBackslash() throws Exception {
    String out = strip("<a href=\"C:\\Temp\">folder</a> text after <a href=\"x\">y</a> end");
    assertEquals("folder text after y end", out);
  }

  public void testDoubleQuotedValueEndingInBackslash() throws Exception {
    String out = strip("<a href=\"C:\\Temp\\\">folder</a> text after <a href=\"x\">y</a> end");
    assertEquals("folder text after y end", out);
  }

  public void testSingleQuotedValueEndingInBackslash() throws Exception {
    String out = strip("<a title='C:\\Temp\\'>folder</a> text after <a title='x'>y</a> end");
    assertEquals("folder text after y end", out);
  }
}
