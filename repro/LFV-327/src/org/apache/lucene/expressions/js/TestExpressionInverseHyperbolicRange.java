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

import org.apache.lucene.expressions.Expression;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The expressions module binds asinh/acosh/atanh to MathUtil (JavascriptCompiler.properties), so
 * a user expression over a finite argument evaluates to Infinity or 0. Public API only.
 */
public class TestExpressionInverseHyperbolicRange extends LuceneTestCase {

  private static double eval(String source) throws Exception {
    Expression e = JavascriptCompiler.compile(source);
    return e.evaluate(null);
  }

  public void testAsinhOfLargeLiteral() throws Exception {
    double v = eval("asinh(1e200)");
    assertTrue("asinh(1e200) = " + v, Double.isFinite(v));
    assertEquals(461.2101657793691, v, 1e-9);
  }

  public void testAcoshOfLargeLiteral() throws Exception {
    double v = eval("acosh(1e200)");
    assertTrue("acosh(1e200) = " + v, Double.isFinite(v));
    assertEquals(461.2101657793691, v, 1e-9);
  }

  public void testAsinhAndAtanhOfTinyLiteral() throws Exception {
    assertEquals("asinh(1e-17)", 1e-17, eval("asinh(1e-17)"), 1e-29);
    assertEquals("atanh(1e-17)", 1e-17, eval("atanh(1e-17)"), 1e-29);
  }
}
