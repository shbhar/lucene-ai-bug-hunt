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
package org.apache.lucene.analysis.ckb;

import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

public class TestSoraniFinalHehBeforeRlm extends BaseTokenStreamTestCase {

  public void testFinalHehFollowedByRlm() throws Exception {
    // expected: word-final HEH U+0647 becomes AE U+06D5 and the trailing RLM is removed; actual: HEH
    assertTokenStreamContents(
        new SoraniNormalizationFilter(keywordMockTokenizer("\u062E\u0627\u0646\u0647\u200F")),
        new String[] {"\u062E\u0627\u0646\u06D5"});
  }
}
