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
package org.apache.lucene.analysis.cn.smart;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.index.RandomIndexWriter;

/** SmartChineseAnalyzer#normalize must return the form that indexing stores. */
public class TestSmartChineseAnalyzerNormalize extends BaseTokenStreamTestCase {

  /** The tokenizer folds full-width letters and digits to half-width (see testFullWidth). */
  public void testNormalizeFoldsFullWidthLikeIndexing() throws IOException {
    try (Analyzer a = new SmartChineseAnalyzer()) {
      checkOneTerm(a, "ＡＢＣ", "abc"); // what the index holds
      checkOneTerm(a, "１２３", "123");
      assertEquals("abc", a.normalize("f", "ＡＢＣ").utf8ToString());
      assertEquals("123", a.normalize("f", "１２３").utf8ToString());
    }
  }

  /**
   * The query parsers build "Ｌｕｃ*" as new PrefixQuery(new Term(field, normalize(field, "Ｌｕｃ"))),
   * "Ｌｕｃｅｎ~1" as a FuzzyQuery on normalize(field, "Ｌｕｃｅｎ") and range bounds with normalize.
   */
  public void testMultiTermQueriesFindTheDocument() throws IOException {
    try (Analyzer a = new SmartChineseAnalyzer();
        Directory dir = newDirectory();
        RandomIndexWriter w = new RandomIndexWriter(random(), dir, a)) {
      Document doc = new Document();
      doc.add(new TextField("body", "我们使用Ｌｕｃｅｎｅ", Field.Store.NO)); // indexed: 我们, 使用, lucen
      w.addDocument(doc);
      try (IndexReader r = w.getReader()) {
        IndexSearcher s = newSearcher(r);
        Query[] queries = {
          new PrefixQuery(new Term("body", a.normalize("body", "Luc"))), // control
          new PrefixQuery(new Term("body", a.normalize("body", "Ｌｕｃ"))),
          new FuzzyQuery(new Term("body", a.normalize("body", "Ｌｕｃｅｎ")), 1),
          new TermRangeQuery(
              "body", a.normalize("body", "Ｌｕａ"), a.normalize("body", "Ｌｕｚ"), true, true)
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
