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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.DoubleValues;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollectorManager;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * DoubleValuesSource.fromDoubleField declares its decoder Double::longBitsToDouble as INCREASING,
 * and #16431 reads skip-index bounds through it: the block maximum is decode(max raw long). But raw
 * IEEE-754 bits are not ordered like the doubles they encode: NaN's bits are above +Infinity's, and
 * negative doubles are ordered in reverse. (The same holds for fromFloatField.) The values are
 * indexed with the encoding of DoubleDocValuesField (Double.doubleToRawLongBits), plus a skip index.
 */
public class TestDoubleFieldSkipperBounds extends LuceneTestCase {

  // more than one 4096-doc skip-index interval and scoring window
  private static final int NUM_DOCS = 5_000;

  private static Directory index(double[] values, int termDoc) throws Exception {
    Directory dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (int i = 0; i < values.length; i++) {
        Document doc = new Document();
        doc.add(new StringField("body", i == termDoc ? "b" : "x", Field.Store.NO));
        doc.add(NumericDocValuesField.indexedField("rating", Double.doubleToRawLongBits(values[i])));
        w.addDocument(doc);
      }
      w.forceMerge(1);
    }
    return dir;
  }

  /**
   * One NaN value (FunctionScoreQuery scores it 0) turns the bound of its block into NaN, and the
   * disjunction then skips the block's best document.
   */
  public void testNaNValueTopHit() throws Exception {
    double[] values = new double[NUM_DOCS];
    java.util.Arrays.fill(values, 0.5);
    values[0] = 1.0;
    values[4500] = Double.NaN;
    values[NUM_DOCS - 1] = 50.0; // the best hit
    try (Directory dir = index(values, 1);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      Query query =
          new BooleanQuery.Builder()
              .add(
                  new FunctionScoreQuery(
                      MatchAllDocsQuery.INSTANCE, DoubleValuesSource.fromDoubleField("rating")),
                  Occur.SHOULD)
              .add(new TermQuery(new Term("body", "b")), Occur.SHOULD)
              .build();
      TopDocs complete =
          searcher.search(query, new TopScoreDocCollectorManager(1, null, Integer.MAX_VALUE));
      TopDocs topScores = searcher.search(query, new TopScoreDocCollectorManager(1, null, 1));
      assertEquals(NUM_DOCS - 1, complete.scoreDocs[0].doc);
      assertEquals(
          "TOP_SCORES top hit (score " + topScores.scoreDocs[0].score + ") differs from COMPLETE",
          complete.scoreDocs[0].doc,
          topScores.scoreDocs[0].doc);
    }
  }

  /** The value bounds must contain every value of the block (as TestDoubleValuesSource checks). */
  public void testNegativeValueBounds() throws Exception {
    double[] values = new double[NUM_DOCS];
    java.util.Arrays.fill(values, -2.0);
    values[0] = -1.0; // the maximum
    try (Directory dir = index(values, -1);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      LeafReaderContext leaf = reader.leaves().get(0);
      DoubleValues dv = DoubleValuesSource.fromDoubleField("rating").getValues(leaf, null);
      int upTo = dv.advanceShallow(0);
      assertTrue(dv.advanceExact(0));
      double value = dv.doubleValue();
      assertEquals(-1.0, value, 0d);
      float max = dv.getMaxScore(upTo);
      float min = dv.getMinScore(upTo);
      assertTrue("value " + value + " > getMaxScore(" + upTo + ") = " + max, value <= max);
      assertTrue("min " + min + " must be <= -2.0", min <= -2.0);
    }
  }
}
