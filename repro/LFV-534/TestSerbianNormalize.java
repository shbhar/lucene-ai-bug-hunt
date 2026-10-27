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
package org.apache.lucene.analysis.sr;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.index.RandomIndexWriter;

/** SerbianAnalyzer#normalize must return the form that indexing stores. */
public class TestSerbianNormalize extends BaseTokenStreamTestCase {

  /** Stemming is switched off for these words, so indexing only lower-cases and normalizes. */
  public void testNormalizeReturnsIndexedForm() throws IOException {
    CharArraySet noStem = new CharArraySet(List.of("ђак", "čačak"), false);
    try (Analyzer a = new SerbianAnalyzer(CharArraySet.EMPTY_SET, noStem)) {
      checkOneTerm(a, "Ђак", "djak"); // what the index holds
      checkOneTerm(a, "Čačak", "cacak");
      assertEquals("djak", a.normalize("f", "Ђак").utf8ToString());
      assertEquals("cacak", a.normalize("f", "Čačak").utf8ToString());
    }
  }

  /**
   * The query parsers build "Беогр*" as new PrefixQuery(new Term(field, normalize(field, "Беогр")))
   * and "Београд~1" as a FuzzyQuery on normalize(field, "Београд").
   */
  public void testMultiTermQueriesFindTheDocument() throws IOException {
    try (Analyzer a = new SerbianAnalyzer();
        Directory dir = newDirectory();
        RandomIndexWriter w = new RandomIndexWriter(random(), dir, a)) {
      Document doc = new Document();
      doc.add(new TextField("body", "Београд Čačak", Field.Store.NO)); // indexed: beograd, cack
      w.addDocument(doc);
      try (IndexReader r = w.getReader()) {
        IndexSearcher s = newSearcher(r);
        Query[] queries = {
          new PrefixQuery(new Term("body", a.normalize("body", "Beogr"))), // control
          new PrefixQuery(new Term("body", a.normalize("body", "Cac"))), // control
          new PrefixQuery(new Term("body", a.normalize("body", "Беогр"))),
          new PrefixQuery(new Term("body", a.normalize("body", "Čač"))),
          new FuzzyQuery(new Term("body", a.normalize("body", "Београд")), 1)
        };
        List<String> misses = new ArrayList<>();
        for (Query q : queries) {
          if (s.count(q) != 1) {
            misses.add(q.toString());
          }
        }
        assertEquals("queries that miss the document", List.of(), misses);
      }
    }
  }
}
