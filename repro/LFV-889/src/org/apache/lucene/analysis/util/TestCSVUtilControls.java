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
package org.apache.lucene.analysis.util;

import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Expected to PASS on the unfixed code. Controls that show (a) parse does intend to undo
 * quoteEscape (comma-only and quote-only values round-trip), and (b) the encoder writes a value
 * consisting of one double quote as the two characters "" with no enclosing quotes, so in this
 * encoder's dialect the field "" means one quote character, not the empty string.
 */
public class TestCSVUtilControls extends LuceneTestCase {

  private static String firstField(String value) {
    String[] fields = CSVUtil.parse(CSVUtil.quoteEscape(value) + ",tail");
    assertEquals(2, fields.length);
    return fields[0];
  }

  public void testCommaOnlyRoundTrips() {
    assertEquals("To be, or not to be", firstField("To be, or not to be"));
  }

  public void testQuoteOnlyRoundTrips() {
    assertEquals("\"Let It Be\" is a song", firstField("\"Let It Be\" is a song"));
  }

  public void testPlainRoundTrips() {
    assertEquals("plain", firstField("plain"));
  }

  public void testEmptyValueRoundTrips() {
    assertEquals("", CSVUtil.quoteEscape(""));
    assertEquals("", firstField(""));
  }

  /**
   * quoteEscape of a single double-quote character is literally the field "" (unenclosed). parse
   * decodes that field to one quote character, which is the correct inverse for this encoder. The
   * same line "",tail is the finder's "empty quoted field" input.
   */
  public void testSingleQuoteValueEncodesAsTwoQuotes() {
    assertEquals("\"\"", CSVUtil.quoteEscape("\""));
    assertEquals("\"", firstField("\""));
    String[] fields = CSVUtil.parse("\"\",tail");
    assertEquals("\"", fields[0]);
  }
}
