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
 * HTMLStripCharFilter decodes a hexadecimal character reference only when it has at most five hex
 * digits (the length check counts the 'x'), although the code comment says "10FFFF: max 6 hex
 * chars" and the decimal branch accepts all seven digits of 1114111. So &amp;#x10FFFF; and
 * zero-padded six-digit references such as &amp;#x0000E9; stay in the output as literal text,
 * while their decimal equivalents are decoded. Public API only.
 */
public class TestHTMLStripHexCharRefDigits extends LuceneTestCase {

  private static String strip(String html) throws Exception {
    try (Reader r = new HTMLStripCharFilter(new StringReader(html))) {
      StringBuilder sb = new StringBuilder();
      int c;
      while ((c = r.read()) != -1) {
        sb.append((char) c);
      }
      return sb.toString();
    }
  }

  /** Controls: five hex digits and the decimal forms are decoded. */
  public void testControls() throws Exception {
    assertEquals("😀", strip("&#x1F600;"));
    assertEquals("é", strip("&#x000E9;"));
    assertEquals("é", strip("&#0000233;"));
    assertEquals(new String(Character.toChars(0x10FFFF)), strip("&#1114111;"));
  }

  public void testSixHexDigitsMaxCodePoint() throws Exception {
    assertEquals(new String(Character.toChars(0x10FFFF)), strip("&#x10FFFF;"));
  }

  public void testSixHexDigitsPlane16() throws Exception {
    assertEquals(new String(Character.toChars(0x100000)), strip("&#x100000;"));
  }

  public void testZeroPaddedSixHexDigits() throws Exception {
    assertEquals("é", strip("&#x0000E9;"));
  }
}
