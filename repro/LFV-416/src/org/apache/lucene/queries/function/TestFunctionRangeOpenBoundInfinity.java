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
import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleDocValuesField;
import org.apache.lucene.document.FloatDocValuesField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queries.function.valuesource.DoubleFieldSource;
import org.apache.lucene.queries.function.valuesource.FloatFieldSource;
import org.apache.lucene.queries.function.valuesource.LongFieldSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A null FunctionRangeQuery bound is open ('*' in toString). LongDocValues/IntDocValues ignore the
 * include flag of a null bound, so {* TO 5] keeps Long.MIN_VALUE; DoubleDocValues and the float
 * default turn the null bound into an infinity and apply the strict comparison to it, so {* TO 5]
 * drops -Infinity and [0 TO *} drops +Infinity.
 */
public class TestFunctionRangeOpenBoundInfinity extends LuceneTestCase {

  private static Set<String> ids(IndexSearcher s, Query q) throws IOException {
    Set<String> ids = new TreeSet<>();
    for (ScoreDoc sd : s.search(q, 10).scoreDocs) ids.add(s.storedFields().document(sd.doc).get("id"));
    return ids;
  }

  public void testOpenExclusiveBounds() throws Exception {
    String[] names = {"neg", "zero", "pos"};
    double[] ds = {Double.NEGATIVE_INFINITY, 0, Double.POSITIVE_INFINITY};
    long[] ls = {Long.MIN_VALUE, 0, Long.MAX_VALUE};
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (int i = 0; i < 3; i++) {
          Document doc = new Document();
          doc.add(new StoredField("id", names[i]));
          doc.add(new DoubleDocValuesField("d", ds[i]));
          doc.add(new FloatDocValuesField("f", (float) ds[i]));
          doc.add(new NumericDocValuesField("l", ls[i]));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        // plain searcher: the asserting searcher would first trip over the negative scores of FunctionRangeQuery
        IndexSearcher s = new IndexSearcher(r);
        // control: the long source treats the null bound as open
        assertEquals(Set.of("neg", "zero"), ids(s, new FunctionRangeQuery(new LongFieldSource("l"), null, 5L, false, true)));
        assertEquals(Set.of("zero", "pos"), ids(s, new FunctionRangeQuery(new LongFieldSource("l"), 0L, null, true, false)));
        // double and float sources
        assertEquals("double {* TO 5]", Set.of("neg", "zero"),
            ids(s, new FunctionRangeQuery(new DoubleFieldSource("d"), null, 5.0, false, true)));
        assertEquals("double [0 TO *}", Set.of("zero", "pos"),
            ids(s, new FunctionRangeQuery(new DoubleFieldSource("d"), 0.0, null, true, false)));
        assertEquals("float {* TO 5]", Set.of("neg", "zero"),
            ids(s, new FunctionRangeQuery(new FloatFieldSource("f"), null, 5f, false, true)));
      }
    }
  }
}
