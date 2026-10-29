/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
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
 * CSVUtil.parse unquotes every field that is followed by a comma, but appends the last field of
 * the line as-is, so the same quoted value decodes differently depending on its position, and
 * parse does not invert quoteEscape for a single value or for the last value of a line.
 */
public class TestCSVUtilLastField extends LuceneTestCase {

  public void testQuotedFieldDecodesTheSameInEveryPosition() {
    String[] fields = CSVUtil.parse("\"a,b\",\"c,d\"");
    assertEquals(2, fields.length);
    assertEquals("a,b", fields[0]); // passes
    assertEquals("c,d", fields[1]); // actual: "\"c,d\"" (quotes kept)
  }

  public void testParseInvertsQuoteEscapeForTheLastField() {
    String value = "To be, or not to be";
    String[] fields = CSVUtil.parse("x," + CSVUtil.quoteEscape(value));
    assertArrayEquals(new String[] {"x", value}, fields); // actual: [x, "To be, or not to be"]
  }
}
