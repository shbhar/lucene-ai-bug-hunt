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
package org.apache.lucene.search.uhighlight;

import java.util.EnumSet;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.uhighlight.UnifiedHighlighter.HighlightFlag;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestUnifiedHighlighterBuilderWithFlags extends LuceneTestCase {

  public void testWithFlagsIsHonoured() throws Exception {
    UnifiedHighlighter uh =
        UnifiedHighlighter.builderWithoutSearcher(new StandardAnalyzer())
            .withFlags(EnumSet.of(HighlightFlag.MULTI_TERM_QUERY))
            .build();
    PhraseQuery query = new PhraseQuery("body", "alpha", "beta");
    // expected: no PHRASES flag, so every query term is highlighted; actual: strict phrase match
    assertEquals(
        "<b>alpha</b> <b>beta</b> <b>alpha</b> gamma",
        uh.highlightWithoutSearcher("body", query, "alpha beta alpha gamma", 1));
  }
}
