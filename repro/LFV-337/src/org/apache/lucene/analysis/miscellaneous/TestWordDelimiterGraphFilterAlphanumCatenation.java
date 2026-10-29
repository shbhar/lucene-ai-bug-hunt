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

import static org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter.CATENATE_NUMBERS;
import static org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter.GENERATE_NUMBER_PARTS;
import static org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter.GENERATE_WORD_PARTS;

import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

public class TestWordDelimiterGraphFilterAlphanumCatenation extends BaseTokenStreamTestCase {

  public void testCatenateNumbersJoinsOnlyAdjacentParts() throws Exception {
    byte[] types = WordDelimiterIterator.DEFAULT_WORD_DELIM_TABLE.clone();
    types['#'] = WordDelimiterIterator.ALPHANUM;
    int flags = GENERATE_WORD_PARTS | GENERATE_NUMBER_PARTS | CATENATE_NUMBERS;
    // expected [#x, ab, 12]; actual adds "#x12", catenating "#x" and "12" across "ab"
    assertTokenStreamContents(
        new WordDelimiterGraphFilter(whitespaceMockTokenizer("#x-ab-12"), false, types, flags, null),
        new String[] {"#x", "ab", "12"});
  }
}
