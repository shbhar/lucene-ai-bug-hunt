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
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queries.function.valuesource.DoubleFieldSource;
import org.apache.lucene.queries.function.valuesource.LongFieldSource;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * FunctionRangeQuery over ValueSource.fromDoubleValuesSource(...) should compare the source's
 * double values with the double bounds, as it does for the same field read via DoubleFieldSource
 * or LongFieldSource. Uses only public API.
 */
public class TestFromDoubleValuesSourceRangePrecision extends LuceneTestCase {

  private static Set<String> ids(IndexSearcher s, Query q) throws IOException {
    Set<String> ids = new TreeSet<>();
    for (ScoreDoc sd : s.search(q, 100).scoreDocs) {
      ids.add(s.storedFields().document(sd.doc).get("id"));
    }
    return ids;
  }

  public void testDoubleField() throws Exception {
    double[] values = {1.0, 1.0000000000000002, 1e300, 1e301};
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (double v : values) {
          Document doc = new Document();
          doc.add(new StoredField("id", Double.toString(v)));
          doc.add(new DoubleDocValuesField("d", v));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        ValueSource direct = new DoubleFieldSource("d");
        ValueSource bridged =
            ValueSource.fromDoubleValuesSource(DoubleValuesSource.fromDoubleField("d"));

        // d == 1.0
        assertEquals(Set.of("1.0"), ids(s, new FunctionRangeQuery(direct, 1.0, 1.0, true, true)));
        assertEquals(
            "[1.0 TO 1.0] via fromDoubleValuesSource",
            Set.of("1.0"),
            ids(s, new FunctionRangeQuery(bridged, 1.0, 1.0, true, true)));
        // d > 1.0
        assertEquals(
            "{1.0 TO *] via fromDoubleValuesSource",
            Set.of("1.0000000000000002", "1.0E300", "1.0E301"),
            ids(s, new FunctionRangeQuery(bridged, 1.0, null, false, true)));
        // d <= 1e300
        assertEquals(
            "[* TO 1e300] via fromDoubleValuesSource",
            Set.of("1.0", "1.0000000000000002", "1.0E300"),
            ids(s, new FunctionRangeQuery(bridged, null, 1e300, true, true)));
      }
    }
  }

  public void testLongField() throws Exception {
    long[] values = {16_777_216L, 16_777_217L};
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (long v : values) {
          Document doc = new Document();
          doc.add(new StoredField("id", Long.toString(v)));
          doc.add(new NumericDocValuesField("l", v));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        ValueSource direct = new LongFieldSource("l");
        ValueSource bridged =
            ValueSource.fromDoubleValuesSource(DoubleValuesSource.fromLongField("l"));
        assertEquals(
            Set.of("16777217"),
            ids(s, new FunctionRangeQuery(direct, 16_777_217L, 16_777_217L, true, true)));
        assertEquals(
            "[16777217 TO 16777217] via fromDoubleValuesSource",
            Set.of("16777217"),
            ids(s, new FunctionRangeQuery(bridged, 16_777_217L, 16_777_217L, true, true)));
      }
    }
  }
}
