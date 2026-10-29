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
package org.apache.lucene.monitor;

import java.util.List;
import java.util.Set;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestRegexpQueryHandlerSupplementaryChar extends LuceneTestCase {

  public void testRegexpMatchesTokenWithEmoji() throws Exception {
    Presearcher presearcher = new TermFilteredPresearcher(TermWeightor.DEFAULT, List.of(new RegexpQueryHandler()), Set.of());
    try (Monitor monitor = new Monitor(new WhitespaceAnalyzer(), presearcher)) {
      monitor.register(new MonitorQuery("q", new RegexpQuery(new Term("f", "hel.*"))));
      Document doc = new Document();
      doc.add(new TextField("f", "hello\uD83D\uDE00", Field.Store.NO));
      MatchingQueries<QueryMatch> matches = monitor.match(doc, QueryMatch.SIMPLE_MATCHER);
      // expected: /hel.*/ matches the one token "hello" + U+1F600; actual: the presearcher never runs the query, 0 matches
      assertEquals("queries run: " + matches.getQueriesRun(), 1, matches.getMatchCount());
    }
  }
}
