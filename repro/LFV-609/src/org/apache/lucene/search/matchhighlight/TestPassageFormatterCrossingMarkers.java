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

public class TestPassageFormatterCrossingMarkers extends LuceneTestCase {

  public void testCrossingMarkersCloseInnermostFirst() {
    PassageFormatter formatter =
        new PassageFormatter(
            "...", r -> r.from == 0 ? "<a>" : "<b>", r -> r.from == 0 ? "</a>" : "</b>");
    Passage passage = new Passage(0, 11, List.of(new OffsetRange(0, 7), new OffsetRange(4, 11)));
    String out = formatter.format(new StringBuilder(), "foo bar baz", passage).toString();
    // expected: <b> (opened last) closes before </a>; actual: "<a>foo <b>bar</a></b><b> baz</b>"
    assertEquals("<a>foo <b>bar</b></a><b> baz</b>", out);
  }
}
