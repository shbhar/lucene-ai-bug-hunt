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
package org.apache.lucene.queries.function;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopScoreDocCollectorManager;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/** FunctionScoreQuery over a skip-indexed field must report a max score that bounds its scores. */
public class TestFunctionScoreQuerySkipIndexBound extends LuceneTestCase {

  private static final int NUM_DOCS = 5000;

  /** All docs match body:a, only doc 0 matches body:b, only the last doc has popularity 100. */
  private static Directory index() throws IOException {
    Directory dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (int i = 0; i < NUM_DOCS; i++) {
        Document doc = new Document();
        doc.add(new StringField("body", "a", Field.Store.NO));
        if (i == 0) {
          doc.add(new StringField("body", "b", Field.Store.NO));
        }
        // indexedField = numeric doc values with a skip index
        doc.add(NumericDocValuesField.indexedField("popularity", i == NUM_DOCS - 1 ? 100 : 1));
        w.addDocument(doc);
      }
      w.forceMerge(1);
    }
    return dir;
  }

  /** Scores each doc matching body:a by its popularity. */
  private static Query popularityOfA() {
    return new FunctionScoreQuery(
        new TermQuery(new Term("body", "a")), DoubleValuesSource.fromLongField("popularity"));
  }

  public void testTopHitOfBooleanQuery() throws IOException {
    try (Directory dir = index();
        DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      Query query =
          new BooleanQuery.Builder()
              .add(popularityOfA(), Occur.SHOULD)
              .add(new TermQuery(new Term("body", "b")), Occur.SHOULD)
              .build();
      // exhaustive scoring (no pruning): the last doc wins with score 100
      ScoreDoc exhaustive =
          searcher.search(query, new TopScoreDocCollectorManager(1, Integer.MAX_VALUE))
              .scoreDocs[0];
      assertEquals(NUM_DOCS - 1, exhaustive.doc);
      assertEquals(100f, exhaustive.score, 0f);
      // the default search(query, n) prunes with block-max bounds after 1,000 hits
      ScoreDoc top = searcher.search(query, 1).scoreDocs[0];
      assertEquals("search(query, 1) top hit has score " + top.score, NUM_DOCS - 1, top.doc);
    }
  }

  public void testMaxScoreIsAnUpperBound() throws IOException {
    try (Directory dir = index();
        DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      Scorer scorer =
          searcher
              .createWeight(searcher.rewrite(popularityOfA()), ScoreMode.TOP_SCORES, 1f)
              .scorer(reader.leaves().get(0));
      int upTo = scorer.advanceShallow(0);
      float maxScore = scorer.getMaxScore(upTo);
      DocIdSetIterator it = scorer.iterator();
      for (int doc = it.nextDoc();
          doc != DocIdSetIterator.NO_MORE_DOCS && doc <= upTo;
          doc = it.nextDoc()) {
        float score = scorer.score();
        assertTrue(
            "doc " + doc + " scores " + score + " > getMaxScore(" + upTo + ") = " + maxScore,
            score <= maxScore);
      }
    }
  }
}
