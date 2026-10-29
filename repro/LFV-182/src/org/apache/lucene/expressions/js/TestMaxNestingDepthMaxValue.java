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

public class TestMaxNestingDepthMaxValue extends LuceneTestCaseJupiter {

  @Test
  public void testMaxValueLimitCompilesTrivialExpression() {
    // expected: "1" compiles; actual: ParseException because maxNestingDepth + 1 wraps around
    try {
      JavascriptCompiler.compile("1", JavascriptCompiler.DEFAULT_FUNCTIONS, Integer.MAX_VALUE);
    } catch (ParseException e) {
      throw new AssertionError(
          "expected \"1\" to compile with maxNestingDepth=Integer.MAX_VALUE, but got: " + e, e);
    }
  }
}
