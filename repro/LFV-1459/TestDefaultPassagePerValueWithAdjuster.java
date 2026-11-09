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

public class TestDefaultPassagePerValueWithAdjuster extends LuceneTestCase {

  public void testOneDefaultPassagePerValue() {
    // Expected 2 passages (one per value); actual: 1 with -da, AssertionError with -ea.
    List<Passage> passages =
        FieldValueHighlighters.defaultPassageSelector()
            .pickBest(
                "01234 56789",
                List.of(),
                4,
                2,
                List.of(new OffsetRange(0, 5), new OffsetRange(6, 11)));
    assertEquals(passages.toString(), 2, passages.size());
  }
}
