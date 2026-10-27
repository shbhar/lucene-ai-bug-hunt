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
package org.apache.lucene.search;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * When the inner query matches no document, RescoreTopNQuery must match no document. Instead its
 * rewrite passes an empty TopDocs to DocAndScoreQuery.createDocAndScoreQuery, whose javadoc says
 * the input "must be non-empty": AssertionError with -ea, ArrayIndexOutOfBoundsException with -da.
 */
public class TestRescoreTopNQueryNoCandidates extends LuceneTestCase {

  private static Directory index() throws Exception {
    Directory dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (int i = 0; i < 3; i++) {
        Document doc = new Document();
        doc.add(new StringField("color", "red", Field.Store.NO));
        doc.add(
            new KnnFloatVectorField(
                "vector", new float[] {1, i}, VectorSimilarityFunction.EUCLIDEAN));
        w.addDocument(doc);
      }
    }
    return dir;
  }

  /** Full-precision rescoring of a filtered kNN query whose filter matches nothing. */
  public void testFilteredKnnWithoutMatches() throws Exception {
    try (Directory dir = index();
        IndexReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      float[] target = {1, 1};
      Query knn =
          new KnnFloatVectorQuery("vector", target, 10, new TermQuery(new Term("color", "blue")));
      assertEquals(0, searcher.count(knn));

      Query rescore = RescoreTopNQuery.createFullPrecisionRescorerQuery(knn, target, "vector", 5);
      assertEquals(0, searcher.search(rescore, 5).scoreDocs.length);
      assertEquals(0, searcher.count(rescore));
    }
  }

  /** Any inner query without matches. */
  public void testTermQueryWithoutMatches() throws Exception {
    try (Directory dir = index();
        IndexReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      Query rescore =
          new RescoreTopNQuery(
              new TermQuery(new Term("color", "blue")), DoubleValuesSource.constant(1), 5);
      assertEquals(0, searcher.search(rescore, 5).scoreDocs.length);
    }
  }

  /** Control: the same queries with a filter or term that matches pass. */
  public void testControlWithMatches() throws Exception {
    try (Directory dir = index();
        IndexReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      float[] target = {1, 1};
      Query knn =
          new KnnFloatVectorQuery("vector", target, 10, new TermQuery(new Term("color", "red")));
      Query rescore = RescoreTopNQuery.createFullPrecisionRescorerQuery(knn, target, "vector", 2);
      assertEquals(2, searcher.search(rescore, 5).scoreDocs.length);
      assertEquals(2, searcher.count(rescore));
      Query term =
          new RescoreTopNQuery(
              new TermQuery(new Term("color", "red")), DoubleValuesSource.constant(1), 5);
      assertEquals(3, searcher.search(term, 5).scoreDocs.length);
    }
  }
}
