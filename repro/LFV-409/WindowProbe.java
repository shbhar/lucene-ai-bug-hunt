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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queries.function.FunctionRangeQuery;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.queries.function.valuesource.LongFieldSource;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

/**
 * How wide is the window of wrongly matched long values? Indexes every integer in [from, to] in a
 * long doc-values field and prints the count, smallest and largest match of a range over the
 * typed LongFieldSource and over ValueSource.fromDoubleValuesSource(DoubleValuesSource.fromLongField).
 * Public API only; default IndexWriterConfig and codec.
 */
public class WindowProbe {

  public static void main(String[] args) throws Exception {
    long id = 1_000_000_000L;
    probe("id", id - 100, id + 100, id, id);
    long t = 1_700_000_000_000L;
    probe("time", t - 40_000, t + 120_000, t, t);
    probe("time", t - 40_000, t + 120_000, null, t);
  }

  static void probe(String field, long from, long to, Long lower, Long upper) throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (long v = from; v <= to; v++) {
          Document doc = new Document();
          doc.add(new NumericDocValuesField(field, v));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        String range = "[" + (lower == null ? "*" : lower) + " TO " + upper + "]";
        Query typed = new FunctionRangeQuery(new LongFieldSource(field), lower, upper, true, true);
        Query bridged =
            new FunctionRangeQuery(
                ValueSource.fromDoubleValuesSource(DoubleValuesSource.fromLongField(field)),
                lower,
                upper,
                true,
                true);
        System.out.println(
            field + " values " + from + ".." + to + ", " + range + ": typed " + describe(s, typed, field));
        System.out.println(
            field + " values " + from + ".." + to + ", " + range + ": bridged " + describe(s, bridged, field));
      }
    }
  }

  static String describe(IndexSearcher s, Query q, String field) throws Exception {
    int count = s.count(q);
    if (count == 0) {
      return "count=0";
    }
    long min = first(s, q, field, false);
    long max = first(s, q, field, true);
    return "count=" + count + " min=" + min + " max=" + max;
  }

  static long first(IndexSearcher s, Query q, String field, boolean reverse) throws Exception {
    TopDocs td = s.search(q, 1, new Sort(new SortField(field, SortField.Type.LONG, reverse)));
    return (Long) ((org.apache.lucene.search.FieldDoc) td.scoreDocs[0]).fields[0];
  }
}
