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
package org.apache.lucene.search.matchhighlight;

import java.util.List;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestMatchHighlightWindowSurrogatePair extends LuceneTestCase {

  public void testLeadingPassageDoesNotSplitSurrogatePair() {
    // "BOLD" in MATHEMATICAL BOLD CAPITAL letters (U+1D401 U+1D40E U+1D40B U+1D403, 2 units each)
    String value = "\uD835\uDC01\uD835\uDC0E\uD835\uDC0B\uD835\uDC03 text";
    Passage p =
        FieldValueHighlighters.defaultPassageSelector().pickBest(value, List.of(), 5, 1).get(0);
    assertFalse(
        "expected a passage ending on a code point boundary, actual: " + p,
        Character.isLowSurrogate(value.charAt(p.to)));
  }
}
