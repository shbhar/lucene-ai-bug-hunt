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
package org.apache.lucene.expressions;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.expressions.js.JavascriptCompiler;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Rescorer;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCaseJupiter;
import org.junit.jupiter.api.Test;

/**
 * ExpressionRescorer.explain hands SortRescorer's explanation (whose value is always 0) to
 * ExpressionValueSource.explain as the score explanation, so an expression that uses _score is
 * explained with _score = 0: the explained value differs from the value the rescorer sorted by.
 */
public class TestExpressionRescorerExplainScore extends LuceneTestCaseJupiter {

  @Test
  public void testExplainedValueEqualsRescoredValue() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        for (int i = 0; i < 3; i++) {
          Document doc = new Document();
          doc.add(new TextField("body", "contents" + " filler".repeat(i), Field.Store.NO));
          doc.add(new NumericDocValuesField("popularity", 5 + i));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        Query query = new TermQuery(new Term("body", "contents"));
        TopDocs firstPass = searcher.search(query, 10);
        assertEquals(3, firstPass.scoreDocs.length);

        Expression expr = JavascriptCompiler.compile("_score * 100 + popularity");
        SimpleBindings bindings = new SimpleBindings();
        bindings.add("_score", DoubleValuesSource.SCORES);
        bindings.add("popularity", DoubleValuesSource.fromIntField("popularity"));
        Rescorer rescorer = expr.getRescorer(bindings);
        TopDocs rescored = rescorer.rescore(searcher, firstPass, 10);

        for (ScoreDoc hit : rescored.scoreDocs) {
          double rescoredValue = (Double) ((FieldDoc) hit).fields[0];
          Explanation firstPassExpl = searcher.explain(query, hit.doc);
          Explanation expl = rescorer.explain(searcher, firstPassExpl, hit.doc);
          assertTrue(expl.isMatch());
          // the value the rescorer sorted by is _score * 100 + popularity with the first-pass score
          assertTrue("first-pass score is positive", firstPassExpl.getValue().doubleValue() > 0);
          assertEquals(
              "explained value of doc " + hit.doc + "\n" + expl,
              rescoredValue,
              expl.getValue().doubleValue(),
              1e-4);
        }
      }
    }
  }
}
