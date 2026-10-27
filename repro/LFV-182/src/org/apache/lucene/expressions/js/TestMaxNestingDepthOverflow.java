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

import org.apache.lucene.tests.util.LuceneTestCaseJupiter;
import org.junit.jupiter.api.Test;

/**
 * JavascriptCompiler installs its parse-time nesting listener with a limit of maxNestingDepth + 1.
 * For maxNestingDepth = Integer.MAX_VALUE (the natural way to ask for "no practical limit") the
 * addition overflows to Integer.MIN_VALUE, and every expression, even "1", is rejected with
 * "Nesting level too deep (>-2147483648)".
 */
public class TestMaxNestingDepthOverflow extends LuceneTestCaseJupiter {

  @Test
  public void testLargeLimitAcceptsTrivialExpression() throws Exception {
    // a limit one below the maximum works
    assertEquals(
        3.0,
        JavascriptCompiler.compile(
                "1 + 2", JavascriptCompiler.DEFAULT_FUNCTIONS, Integer.MAX_VALUE - 1)
            .evaluate(null),
        0.0);
    // the maximum limit must accept at least as much
    assertEquals(
        3.0,
        JavascriptCompiler.compile("1 + 2", JavascriptCompiler.DEFAULT_FUNCTIONS, Integer.MAX_VALUE)
            .evaluate(null),
        0.0);
  }
}
