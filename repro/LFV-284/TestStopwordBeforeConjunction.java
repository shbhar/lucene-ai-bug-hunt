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
package org.apache.lucene.queryparser.classic;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenFilter;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * With the default splitOnWhitespace=false, consecutive plain terms are parsed together
 * (QueryParser.jj MultiTerm) and, since #16446, added as ONE clause. When a stopword sits between
 * such a run and an AND/OR, the stopword's clause is null and the conjunction adjusts the run's
 * clause instead of the term just before the stopword, so the stopword changes which documents
 * match. Without the stopword, with splitOnWhitespace=true, and in 10.5.2, the conjunction applies
 * to the last real term.
 */
public class TestStopwordBeforeConjunction extends LuceneTestCase {

  private static Analyzer stopAnalyzer() {
    return new MockAnalyzer(
        random(), MockTokenizer.WHITESPACE, true, MockTokenFilter.ENGLISH_STOPSET);
  }

  private static int count(Directory dir, Query q) throws Exception {
    try (IndexReader r = DirectoryReader.open(dir)) {
      return new IndexSearcher(r).count(q);
    }
  }

  private Directory index(String text) throws Exception {
    Directory dir = newDirectory();
    try (RandomIndexWriter w =
        new RandomIndexWriter(random(), dir, new IndexWriterConfig(stopAnalyzer()))) {
      Document d = new Document();
      d.add(new TextField("body", text, Field.Store.NO));
      w.addDocument(d);
    }
    return dir;
  }

  /** OR (default) operator: "banana" must stay required whether or not "the" is typed. */
  public void testAndAfterStopwordDefaultOperatorOr() throws Exception {
    try (Directory dir = index("apple cherry")) {
      QueryParser qp = new QueryParser("body", stopAnalyzer());
      Query withoutStopword = qp.parse("apple banana AND cherry");
      assertEquals(withoutStopword.toString(), 0, count(dir, withoutStopword));
      Query withStopword = qp.parse("apple banana the AND cherry");
      assertEquals(withStopword.toString(), 0, count(dir, withStopword));
    }
  }

  /** AND operator: "apple" must stay required whether or not "the" is typed. */
  public void testOrAfterStopwordDefaultOperatorAnd() throws Exception {
    try (Directory dir = index("cherry")) {
      QueryParser qp = new QueryParser("body", stopAnalyzer());
      qp.setDefaultOperator(QueryParser.Operator.AND);
      Query withoutStopword = qp.parse("apple banana OR cherry");
      assertEquals(withoutStopword.toString(), 0, count(dir, withoutStopword));
      Query withStopword = qp.parse("apple banana the OR cherry");
      assertEquals(withStopword.toString(), 0, count(dir, withStopword));
    }
  }
}
