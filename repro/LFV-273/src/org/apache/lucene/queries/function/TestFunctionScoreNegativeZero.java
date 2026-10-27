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
import org.apache.lucene.document.FloatDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.function.valuesource.FloatFieldSource;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * LUCENE-7996 made FunctionScoreQuery and FunctionQuery return 0 for negative function values
 * ("Scorers are no longer allowed to produce negative scores"). Both checks are written as {@code
 * value >= 0}, which is true for -0.0, so a function value of -0.0f becomes a score of -0.0f. The
 * test framework's AssertingScorer rejects that ({@code Float.compare(score, 0f) >= 0}), and
 * TopScoreDocCollector then ranks the two zeros inconsistently: the top-1 hit is not the first of
 * the top-2 hits.
 */
public class TestFunctionScoreNegativeZero extends LuceneTestCase {

  private Directory dir;
  private IndexReader reader;
  private IndexSearcher searcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      // doc 0: value -0.0f, doc 1: value 0.0f, one segment
      for (float v : new float[] {-0f, 0f}) {
        Document d = new Document();
        d.add(new StringField("k", "x", Field.Store.NO));
        d.add(new FloatDocValuesField("f", v));
        w.addDocument(d);
      }
    }
    reader = DirectoryReader.open(dir);
    searcher = new IndexSearcher(reader); // plain searcher: no executor, no asserting wrappers
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  private static Query functionScoreQuery() {
    return new FunctionScoreQuery(
        new TermQuery(new Term("k", "x")), DoubleValuesSource.fromFloatField("f"));
  }

  private static void assertNoNegativeScore(TopDocs td) {
    for (ScoreDoc sd : td.scoreDocs) {
      assertTrue(
          "doc " + sd.doc + " has score " + sd.score + " (scores must not be negative)",
          Float.compare(sd.score, 0f) >= 0);
    }
  }

  public void testFunctionScoreQueryNegativeZeroValue() throws IOException {
    assertNoNegativeScore(searcher.search(functionScoreQuery(), 2));
  }

  public void testFunctionQueryNegativeZeroValue() throws IOException {
    assertNoNegativeScore(searcher.search(new FunctionQuery(new FloatFieldSource("f")), 2));
  }

  public void testTopOneIsFirstOfTopTwo() throws IOException {
    TopDocs top1 = searcher.search(functionScoreQuery(), 1);
    TopDocs top2 = searcher.search(functionScoreQuery(), 2);
    assertEquals(2, top2.scoreDocs.length);
    assertEquals(
        "top-1 hit must be the first hit of the top-2",
        top2.scoreDocs[0].doc,
        top1.scoreDocs[0].doc);
  }
}
