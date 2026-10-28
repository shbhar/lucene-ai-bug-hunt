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
import java.util.Arrays;
import java.util.Comparator;
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
 * FunctionRangeQuery over ValueSource.fromDoubleValuesSource(...) should select the same documents
 * as FunctionRangeQuery over DoubleFieldSource / LongFieldSource and as FunctionMatchQuery over the
 * same DoubleValuesSource. Public API only.
 */
public class TestFunctionRangeQueryBridgedPrecision extends LuceneTestCase {

  /** 1.0 and its two neighbouring doubles: [1.0 TO 1.0] must match only 1.0. */
  public void testDoublePointRange() throws Exception {
    try (Directory dir = newDirectory()) {
      index(dir, "d", 0.9999999999999999, 1.0, 1.0000000000000002);
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        DoubleValuesSource dvs = DoubleValuesSource.fromDoubleField("d");
        Query typed = new FunctionRangeQuery(new DoubleFieldSource("d"), 1.0, 1.0, true, true);
        Query match = new FunctionMatchQuery(dvs, v -> v >= 1.0 && v <= 1.0);
        Query bridged =
            new FunctionRangeQuery(ValueSource.fromDoubleValuesSource(dvs), 1.0, 1.0, true, true);
        assertEquals("control: DoubleFieldSource", set("1.0"), ids(s, typed));
        assertEquals("control: FunctionMatchQuery", set("1.0"), ids(s, match));
        assertEquals("[1.0 TO 1.0] via fromDoubleValuesSource", set("1.0"), ids(s, bridged));
      }
    }
  }

  /** A finite upper bound above Float.MAX_VALUE: [* TO 1e300] must not match 1e301. */
  public void testUpperBoundAboveFloatMax() throws Exception {
    try (Directory dir = newDirectory()) {
      index(dir, "d", 1e300, 1e301);
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        DoubleValuesSource dvs = DoubleValuesSource.fromDoubleField("d");
        Query typed = new FunctionRangeQuery(new DoubleFieldSource("d"), null, 1e300, true, true);
        Query bridged =
            new FunctionRangeQuery(
                ValueSource.fromDoubleValuesSource(dvs), null, 1e300, true, true);
        assertEquals("control: DoubleFieldSource", set("1.0E300"), ids(s, typed));
        assertEquals("[* TO 1e300] via fromDoubleValuesSource", set("1.0E300"), ids(s, bridged));
      }
    }
  }

  /** Epoch milliseconds in a long field: [* TO t] must not match t + 1 minute. */
  public void testEpochMillisUpperBound() throws Exception {
    long t = 1_700_000_000_000L;
    try (Directory dir = newDirectory()) {
      index(dir, "time", t - 1, t, t + 60_000);
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        DoubleValuesSource dvs = DoubleValuesSource.fromLongField("time");
        Query typed = new FunctionRangeQuery(new LongFieldSource("time"), null, t, true, true);
        Query bridged =
            new FunctionRangeQuery(ValueSource.fromDoubleValuesSource(dvs), null, t, true, true);
        Set<String> expected = set(Long.toString(t - 1), Long.toString(t));
        assertEquals("control: LongFieldSource", expected, ids(s, typed));
        assertEquals("[* TO " + t + "] via fromDoubleValuesSource", expected, ids(s, bridged));
      }
    }
  }

  /** Ids in a long field: [1000000000 TO 1000000000] must match only that id. */
  public void testLongIdPointRange() throws Exception {
    long id = 1_000_000_000L;
    try (Directory dir = newDirectory()) {
      index(dir, "id_l", id - 1, id, id + 1);
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        DoubleValuesSource dvs = DoubleValuesSource.fromLongField("id_l");
        Query typed = new FunctionRangeQuery(new LongFieldSource("id_l"), id, id, true, true);
        Query bridged =
            new FunctionRangeQuery(ValueSource.fromDoubleValuesSource(dvs), id, id, true, true);
        assertEquals("control: LongFieldSource", set(Long.toString(id)), ids(s, typed));
        assertEquals(
            "[" + id + " TO " + id + "] via fromDoubleValuesSource",
            set(Long.toString(id)),
            ids(s, bridged));
      }
    }
  }

  private static void index(Directory dir, String field, double... values) throws IOException {
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (double v : values) {
        Document doc = new Document();
        doc.add(new StoredField("id", Double.toString(v)));
        doc.add(new DoubleDocValuesField(field, v));
        w.addDocument(doc);
      }
    }
  }

  private static void index(Directory dir, String field, long... values) throws IOException {
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (long v : values) {
        Document doc = new Document();
        doc.add(new StoredField("id", Long.toString(v)));
        doc.add(new NumericDocValuesField(field, v));
        w.addDocument(doc);
      }
    }
  }

  /** The stored ids of all hits, in numeric order. */
  private static Set<String> ids(IndexSearcher s, Query q) throws IOException {
    Set<String> ids = set();
    for (ScoreDoc sd : s.search(q, 100).scoreDocs) {
      ids.add(s.storedFields().document(sd.doc).get("id"));
    }
    return ids;
  }

  private static Set<String> set(String... ids) {
    Set<String> set = new TreeSet<>(Comparator.comparingDouble(Double::parseDouble));
    set.addAll(Arrays.asList(ids));
    return set;
  }
}
