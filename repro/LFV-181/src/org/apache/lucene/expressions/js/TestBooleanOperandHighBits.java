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

public class TestBooleanOperandHighBits extends LuceneTestCaseJupiter {

  @Test
  public void testConditionOnOneShiftedLeft32() throws Exception {
    // Expected 1: (1 << 32) is 4294967296, non-zero; actual 0: only its low 32 bits are tested.
    assertEquals(1.0, JavascriptCompiler.compile("(1 << 32) ? 1 : 0").evaluate(null), 0.0);
  }
}
