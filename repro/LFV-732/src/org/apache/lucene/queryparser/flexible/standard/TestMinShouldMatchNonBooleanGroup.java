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
package org.apache.lucene.queryparser.flexible.standard;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenFilter;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The min-should-match operator {@code (...)@N} of StandardQueryParser: the group's query is cast
 * to BooleanQuery unconditionally, so a group whose query is not a BooleanQuery makes parse() throw
 * ClassCastException. Each test parses a query string that StandardQueryParser documents (grouping,
 * min-should-match, group boost, phrase) and checks the hits on a two-document index.
 */
public class TestMinShouldMatchNonBooleanGroup extends LuceneTestCase {

  private static int hits(Analyzer analyzer, String query) throws Exception {
    try (Directory dir = newDirectory()) {
      RandomIndexWriter w = new RandomIndexWriter(random(), dir, analyzer);
      for (String text : new String[] {"quick fox", "lazy dog"}) {
        Document doc = new Document();
        doc.add(new TextField("body", text, Field.Store.NO));
        w.addDocument(doc);
      }
      try (IndexReader r = w.getReader()) {
        w.close();
        StandardQueryParser qp = new StandardQueryParser(analyzer);
        Query q = qp.parse(query, "body"); // throws ClassCastException on main
        return new IndexSearcher(r).count(q);
      }
    }
  }

  /** A stopword leaves one clause in the group: "at least 1 of {fox}" must find "quick fox". */
  public void testStopwordLeavesOneClause() throws Exception {
    Analyzer stop = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, true, MockTokenFilter.ENGLISH_STOPSET);
    assertEquals(1, hits(stop, "(the fox)@1"));
  }

  /** A group with a single clause. */
  public void testSingleClauseGroup() throws Exception {
    assertEquals(1, hits(new MockAnalyzer(random()), "(fox)@1"));
  }

  /** A boosted group: the grammar puts the boost inside the min-should-match node. */
  public void testBoostedGroup() throws Exception {
    assertEquals(2, hits(new MockAnalyzer(random()), "(fox dog)^2@1"));
  }

  /** A group holding one phrase. */
  public void testPhraseGroup() throws Exception {
    assertEquals(1, hits(new MockAnalyzer(random()), "(\"quick fox\")@1"));
  }

  /** Control: the documented form works. */
  public void testControlTwoClauses() throws Exception {
    assertEquals(2, hits(new MockAnalyzer(random()), "(fox dog)@1"));
  }
}
