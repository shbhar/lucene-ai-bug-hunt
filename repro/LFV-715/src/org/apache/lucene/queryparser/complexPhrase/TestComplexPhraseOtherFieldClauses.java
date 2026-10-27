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
package org.apache.lucene.queryparser.complexPhrase;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The class javadoc says the second pass parses the phrase contents "checking all embedded clauses
 * are referring to the same field". The check is done for term, wildcard, fuzzy and range clauses
 * (parse() throws, see TestComplexPhraseQuery: checkBadQuery("\"jo*  id:1 smith\"")), but not for
 * prefix ("id:1*") or regular-expression ("id:/1/") clauses: parse() accepts them, a multi-clause
 * phrase then fails at search time with IllegalArgumentException "Clauses must have same field.",
 * and a single-clause phrase on field name silently searches field id.
 */
public class TestComplexPhraseOtherFieldClauses extends LuceneTestCase {
  private static final String F = "name";

  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;
  private Analyzer analyzer;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, true);
    dir = newDirectory();
    IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(analyzer));
    String[][] docs = {{"john smith", "1"}, {"jane doe", "2"}};
    for (String[] d : docs) {
      Document doc = new Document();
      doc.add(newTextField(F, d[0], Field.Store.YES));
      doc.add(newTextField("id", d[1], Field.Store.YES));
      w.addDocument(doc);
    }
    w.close();
    reader = DirectoryReader.open(dir);
    searcher = newSearcher(reader);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  private void assertRejected(String s) throws Exception {
    ComplexPhraseQueryParser qp = new ComplexPhraseQueryParser(F, analyzer);
    Query q;
    try {
      q = qp.parse(s);
    } catch (Exception expected) {
      return; // the documented outcome: the clause for another field is rejected while parsing
    }
    String outcome;
    try {
      outcome = searcher.search(q, 10).totalHits + " for parsed query " + q + " (rewritten: " + searcher.rewrite(q) + ")";
    } catch (IllegalArgumentException e) {
      outcome = "parse succeeded and search threw " + e;
    }
    fail(s + ": clause for field id nested in a phrase for field " + F + " was not rejected; " + outcome);
  }

  /** Controls: term, fuzzy, wildcard and range clauses for another field are rejected by parse(). */
  public void testControlsRejected() throws Exception {
    assertRejected("\"jo* id:2\"");
    assertRejected("\"jo* id:2~1\"");
    assertRejected("\"jo* id:2?\"");
    assertRejected("\"jo* id:[1 TO 2]\"");
  }

  public void testPrefixClauseForOtherField() throws Exception {
    assertRejected("\"jo* id:2*\"");
  }

  public void testRegexpClauseForOtherField() throws Exception {
    assertRejected("\"jo* id:/2/\"");
  }

  /** A single-clause phrase on field name must not search field id. */
  public void testSingleClauseSearchesOtherField() throws Exception {
    assertRejected("name:\"id:2*\"");
    assertRejected("name:\"id:/2/\"");
  }
}
