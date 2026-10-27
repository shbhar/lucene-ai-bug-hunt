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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * RegexpQueryHandler indexes a RegexpQuery under the "longest static substring" of its pattern
 * (class javadoc), so the Monitor only runs the query for documents that contain that substring.
 * The substring is computed by splitting the pattern text on '.', '*' and "x?" only, so for patterns
 * using other regular-expression operators (or a '*' that repeats a literal) the chosen "substring"
 * is not contained in the matching terms, and Monitor.match silently misses the query.
 *
 * <p>Uses only public API (Monitor, TermFilteredPresearcher, RegexpQueryHandler, MemoryIndex).
 */
public class TestRegexpQueryHandlerOperators extends LuceneTestCase {

  private static final String FIELD = "text";

  public void testRegexpQueriesThatMatchTheDocumentAreReturned() throws IOException {
    // {pattern, document text}: the pattern matches the document's only term.
    String[][] cases = {
      {"colou*r", "color"}, // '*' repeats the literal 'u' (zero times here)
      {"fo+bar", "foobar"}, // '+'
      {"cat|dog", "dog"}, // alternation
      {"analy[sz]e", "analyze"}, // character class
      {"gr(a|e)y", "grey"}, // group
      {"a.?b", "ab"}, // optional '.'
    };
    Analyzer analyzer = new WhitespaceAnalyzer();
    List<String> missed = new ArrayList<>();
    try (Monitor monitor =
        new Monitor(
            analyzer,
            new TermFilteredPresearcher(
                TermWeightor.DEFAULT, List.of(new RegexpQueryHandler()), Set.of()))) {
      for (String[] c : cases) {
        RegexpQuery query = new RegexpQuery(new Term(FIELD, c[0]));
        Document doc = new Document();
        doc.add(new TextField(FIELD, c[1], Field.Store.NO));

        // ground truth: the query matches the document
        assertTrue(
            c[0] + " must match " + c[1],
            MemoryIndex.fromDocument(doc, analyzer).createSearcher().count(query) > 0);

        monitor.clear();
        monitor.register(new MonitorQuery("q", query));
        MatchingQueries<QueryMatch> matches = monitor.match(doc, QueryMatch.SIMPLE_MATCHER);
        if (matches.matches("q") == null) {
          missed.add("/" + c[0] + "/ on \"" + c[1] + "\" (queries run: " + matches.getQueriesRun() + ")");
        }
      }
    }
    assertTrue("Monitor.match missed matching regexp queries: " + missed, missed.isEmpty());
  }
}
