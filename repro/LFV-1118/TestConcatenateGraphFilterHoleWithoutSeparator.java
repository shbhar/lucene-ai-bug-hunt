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
package org.apache.lucene.analysis.miscellaneous;

import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.analysis.CannedTokenStream;
import org.apache.lucene.tests.analysis.Token;

public class TestConcatenateGraphFilterHoleWithoutSeparator extends BaseTokenStreamTestCase {

  public void testHoleUnderLongTokenWithoutSeparator() throws Exception {
    // expected: "playthewho" and "playwho"; actual: only "playwho" (AssertionError with -ea)
    CannedTokenStream in =
        new CannedTokenStream(
            new Token("play", 1, 0, 4, 1),
            new Token("thewho", 1, 5, 12, 2),
            new Token("who", 1, 9, 12, 1));
    assertTokenStreamContents(
        new ConcatenateGraphFilter(in, null, true, 100),
        new String[] {"playthewho", "playwho"},
        null,
        null,
        new int[] {1, 0});
  }
}
