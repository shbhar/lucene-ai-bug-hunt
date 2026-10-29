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

import java.util.Arrays;
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
import org.apache.lucene.search.DoubleValues;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestRawBitsDoubleFieldBounds extends LuceneTestCase {

  private static IndexWriter writer(Directory dir, double[] ratings) throws Exception {
    IndexWriter w =
        new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()));
    for (int i = 0; i < ratings.length; i++) {
      Document doc = new Document();
      doc.add(NumericDocValuesField.indexedField("rating", Double.doubleToRawLongBits(ratings[i])));
      if (i == 1) {
        doc.add(new StringField("body", "b", Field.Store.NO));
      }
      w.addDocument(doc);
    }
    return w;
  }

  public void testMaxScoreOfNegativeValues() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = writer(dir, new double[] {-1.0, -2.0});
        DirectoryReader reader = DirectoryReader.open(w)) {
      DoubleValues values =
          DoubleValuesSource.fromDoubleField("rating").getValues(reader.leaves().get(0), null);
      float max = values.getMaxScore(values.advanceShallow(0));
      assertTrue("expected getMaxScore >= -1.0 (the block's largest value), was " + max, max >= -1);
    }
  }

  public void testNaNValueHidesTopHit() throws Exception {
    double[] ratings = new double[4098];
    Arrays.fill(ratings, 0.5);
    ratings[4096] = Double.NaN;
    ratings[4097] = 50;
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = writer(dir, ratings);
        DirectoryReader reader = DirectoryReader.open(w)) {
      Query query =
          new BooleanQuery.Builder()
              .add(
                  new FunctionScoreQuery(
                      MatchAllDocsQuery.INSTANCE, DoubleValuesSource.fromDoubleField("rating")),
                  Occur.SHOULD)
              .add(new TermQuery(new Term("body", "b")), Occur.SHOULD)
              .build();
      // expected: doc 4097 (rating 50) is the top hit; actual: doc 1 (0.5 plus the term score)
      assertEquals(4097, new IndexSearcher(reader).search(query, 1).scoreDocs[0].doc);
    }
  }
}
