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
package org.apache.lucene.expressions.js;

import java.text.ParseException;
import org.apache.lucene.tests.util.LuceneTestCaseJupiter;
import org.junit.jupiter.api.Test;

/**
 * The grammar accepts hex and octal literals of any length (HEX: [0] [xX] [0-9a-fA-F]+, OCTAL: [0]
 * [0-7]+), but JavascriptCompiler parses them with Long.parseLong. A literal above Long.MAX_VALUE
 * makes compile() throw NumberFormatException, an unchecked exception that compile() does not
 * declare for this case, instead of compiling the number or throwing ParseException.
 */
public class TestHexOctalLiteralOverflow extends LuceneTestCaseJupiter {

  private static void assertCompilesOrParseException(String source, double jsValue) {
    try {
      double v = JavascriptCompiler.compile(source).evaluate(null);
      assertEquals(source, jsValue, v, 0.0);
    } catch (ParseException expected) {
      // acceptable: rejected as a syntax error
    } catch (Exception e) {
      throw new AssertionError("compile(\"" + source + "\") threw " + e, e);
    }
  }

  @Test
  public void testLargestLiteralsStillWork() throws Exception {
    assertEquals(0x1p63, JavascriptCompiler.compile("0x7fffffffffffffff").evaluate(null), 0.0);
    assertEquals(0x1p63, JavascriptCompiler.compile("0777777777777777777777").evaluate(null), 0.0);
  }

  @Test
  public void testHexAboveLongMax() {
    assertCompilesOrParseException("0x8000000000000000", 0x1p63);
  }

  @Test
  public void testOctalAboveLongMax() {
    assertCompilesOrParseException("01000000000000000000000", 0x1p63);
  }

  @Test
  public void testHexAboveLongMaxInsideExpression() {
    assertCompilesOrParseException("1 + 0xffffffffffffffff", 0x1p64);
  }
}
