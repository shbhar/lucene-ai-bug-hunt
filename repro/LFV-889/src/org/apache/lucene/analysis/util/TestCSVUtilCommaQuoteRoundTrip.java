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
 * Expected to FAIL on the unfixed code. A value that contains both a comma and a double quote is
 * encoded by {@link CSVUtil#quoteEscape} as a quoted field with doubled inner quotes (the exact
 * encoding TestCSVUtil#testQuoteEscapeQuotesAndComma asserts). {@link CSVUtil#parse} should decode
 * that field back to the original value. Only non-last fields are used, so the separate "last
 * field is never unquoted" behaviour is not involved. Only public API is used.
 */
public class TestCSVUtilCommaQuoteRoundTrip extends LuceneTestCase {

  private static void assertRoundTripAsFirstField(String value) {
    String encoded = CSVUtil.quoteEscape(value);
    // sanity: the encoder output is the documented/tested shape: '"' + doubled quotes + '"'
    assertEquals('"' + value.replace("\"", "\"\"") + '"', encoded);
    String[] fields = CSVUtil.parse(encoded + ",tail");
    assertEquals("field count for line [" + encoded + ",tail]", 2, fields.length);
    assertEquals("decoded first field of line [" + encoded + ",tail]", value, fields[0]);
    assertEquals("tail", fields[1]);
  }

  public void testShortValueWithCommaAndQuote() {
    assertRoundTripAsFirstField("a \"b\", c");
  }

  /** The same input string TestCSVUtil#testQuoteEscapeQuotesAndComma encodes. */
  public void testUpstreamTestInputRoundTrips() {
    assertRoundTripAsFirstField(
        "\"To be, or not to be ...\" is a well-known phrase from Shakespeare's Hamlet.");
  }

  /** Middle field of a three-field line, written by hand in the encoder's format. */
  public void testMiddleFieldHandWritten() {
    String[] fields = CSVUtil.parse("x,\"1,\"\"2\"\"\",y");
    assertEquals(3, fields.length);
    assertEquals("x", fields[0]);
    assertEquals("1,\"2\"", fields[1]);
    assertEquals("y", fields[2]);
  }
}
