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
package org.apache.lucene.queries.intervals;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Intervals.atLeast(minShouldMatch, sources...) accepts minShouldMatch = 0 (also from the flexible
 * query parser, fn:atLeast(0 a b)); the query is built, but every search that reaches a document
 * containing one of the sources throws NullPointerException. The test asserts that either the
 * argument is rejected up front with IllegalArgumentException or the search runs without an
 * unexpected exception.
 */
public class TestAtLeastZeroMinShouldMatch extends LuceneTestCase {

  public void testAtLeastZero() throws Exception {
    try (Directory dir = newDirectory()) {
      try (RandomIndexWriter w = new RandomIndexWriter(random(), dir, new MockAnalyzer(random()))) {
        Document doc = new Document();
        doc.add(new TextField("f", "a b c", Field.Store.NO));
        w.addDocument(doc);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        Query fromApi;
        try {
          fromApi =
              new IntervalQuery("f", Intervals.atLeast(0, Intervals.term("a"), Intervals.term("b")));
        } catch (IllegalArgumentException rejected) {
          return; // acceptable: invalid argument rejected when the source is built
        }
        Query fromParser =
            new StandardQueryParser(new MockAnalyzer(random())).parse("fn:atLeast(0 a b)", "f");
        StringBuilder failures = new StringBuilder();
        for (Query q : new Query[] {fromApi, fromParser}) {
          try {
            searcher.count(q);
          } catch (NullPointerException e) {
            failures.append("\n  search of ").append(q).append(" threw ").append(e);
          }
        }
        assertEquals("unexpected exceptions:" + failures, 0, failures.length());
      }
    }
  }
}
