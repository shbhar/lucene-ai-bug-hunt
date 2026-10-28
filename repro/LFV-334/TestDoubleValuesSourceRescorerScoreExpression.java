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
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.DoubleValuesSourceRescorer;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Rescorer;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * DoubleValuesSourceRescorer with the Expression javadoc example "sqrt(_score) + ln(popularity)",
 * _score bound to DoubleValuesSource.SCORES. rescore() passes null scores, so the expression fails:
 * an AssertionError with assertions on, and with them off a RuntimeException "Unrecognized variable
 * (_score) referenced in expression (sqrt(_score) + ln(popularity)).".
 */
public class TestDoubleValuesSourceRescorerScoreExpression extends LuceneTestCase {

  private static final int[] POPULARITY = {1, 20, 5, 7};

  /**
   * Indexes four documents, searches body:contents (docs 0, 1 and 2 match) and rescores the 3 hits
   * with the expression. Checks each hit's new score: ln(popularity), plus sqrt(first-pass score)
   * if {@code withScore}.
   */
  private static void check(String expression, boolean withScore) throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        String[] bodies = {"contents contents", "contents", "contents of the book", "other"};
        for (int i = 0; i < bodies.length; i++) {
          Document doc = new Document();
          doc.add(new TextField("body", bodies[i], Field.Store.NO));
          doc.add(new NumericDocValuesField("popularity", POPULARITY[i]));
          w.addDocument(doc);
        }
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        TopDocs firstPass = searcher.search(new TermQuery(new Term("body", "contents")), 3);
        assertEquals(3, firstPass.scoreDocs.length);

        SimpleBindings bindings = new SimpleBindings();
        bindings.add("_score", DoubleValuesSource.SCORES);
        bindings.add("popularity", DoubleValuesSource.fromIntField("popularity"));
        DoubleValuesSource source =
            JavascriptCompiler.compile(expression).getDoubleValuesSource(bindings);
        Rescorer rescorer =
            new DoubleValuesSourceRescorer(source) {
              @Override
              protected float combine(float firstPassScore, boolean valuePresent, double value) {
                return valuePresent ? (float) value : -1f;
              }
            };
        // rescore() updates the ScoreDoc objects in place, so keep the first-pass scores
        float[] firstPassScore = new float[reader.maxDoc()];
        for (ScoreDoc hit : firstPass.scoreDocs) {
          firstPassScore[hit.doc] = hit.score;
        }
        TopDocs rescored = rescorer.rescore(searcher, firstPass, 3);

        assertEquals(3, rescored.scoreDocs.length);
        for (ScoreDoc hit : rescored.scoreDocs) {
          double expected = Math.log(POPULARITY[hit.doc]);
          if (withScore) {
            expected += Math.sqrt(firstPassScore[hit.doc]);
          }
          assertEquals((float) expected, hit.score, 1e-6f);
        }
      }
    }
  }

  /** The Expression javadoc example must combine each hit's first-pass score with popularity. */
  public void testScoreAndPopularity() throws Exception {
    check("sqrt(_score) + ln(popularity)", true);
  }

  /** Control: an expression that does not read _score works. */
  public void testControlPopularityOnly() throws Exception {
    check("ln(popularity)", false);
  }
}
