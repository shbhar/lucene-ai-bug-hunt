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

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * PassageSelector.pickBest centres a passage window of maxPassageWindow chars on each marker (in
 * long, with Math.toIntExact) and then shifts it into the permitted range with an int
 * {@code to += range.from - from}. Upstream TestPassageSelector.largeWindow uses maxPassageWindow =
 * Integer.MAX_VALUE with a single range starting at 0; with a second permitted range (a second value
 * of a multi-valued field, as FieldValueHighlighters.highlighted passes them) the shift exceeds
 * Integer.MAX_VALUE, 'to' wraps negative, and the marker in that range gets no passage.
 */
public class TestPassageSelectorMaxWindowLaterRange extends LuceneTestCase {

  private static String pick(int maxPassageWindow) {
    String value = "aaaa foo bbbb foo"; // two values: [0, 9) "aaaa foo " and [9, 17) "bbbb foo"
    List<OffsetRange> markers = new ArrayList<>(List.of(new OffsetRange(5, 8), new OffsetRange(14, 17)));
    List<OffsetRange> ranges = new ArrayList<>(List.of(new OffsetRange(0, 9), new OffsetRange(9, 17)));
    List<Passage> passages = new PassageSelector().pickBest(value, markers, maxPassageWindow, 10, ranges);
    StringBuilder sb = new StringBuilder();
    for (Passage p : passages) {
      sb.append('[').append(p.from).append(',').append(p.to).append(" markers=").append(p.markers).append(']');
    }
    return sb.toString();
  }

  private static final String BOTH = "[0,9 markers=[[from=5, to=8]]][9,17 markers=[[from=14, to=17]]]";

  /** Control: a window larger than the value, but not near Integer.MAX_VALUE. */
  public void testLargeWindow() {
    assertEquals(BOTH, pick(1000));
    assertEquals(BOTH, pick(Integer.MAX_VALUE - 100));
  }

  /** maxPassageWindow = Integer.MAX_VALUE, as in upstream TestPassageSelector.largeWindow. */
  public void testMaxValueWindowSecondRange() {
    assertEquals(BOTH, pick(Integer.MAX_VALUE));
  }
}
