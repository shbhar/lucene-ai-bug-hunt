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
import java.util.Locale;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FilterLeafReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.queries.function.valuesource.BytesRefFieldSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestFunctionRangeQueryLargeOrdinals extends LuceneTestCase {

  private static final int BASE = 1 << 24;

  private static BytesRef term(int ord) {
    return new BytesRef(String.format(Locale.ROOT, "%09d", ord));
  }

  public void testOrdinalAbove2Pow24() throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        doc.add(new SortedDocValuesField("f", term(BASE)));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        FilterLeafReader leaf =
            new FilterLeafReader(getOnlyLeafReader(reader)) {
              @Override
              public SortedDocValues getSortedDocValues(String field) throws IOException {
                SortedDocValues in = super.getSortedDocValues(field);
                return new SortedDocValues() {
                  @Override
                  public int ordValue() throws IOException {
                    return BASE + in.ordValue();
                  }

                  @Override
                  public BytesRef lookupOrd(int ord) {
                    return term(ord);
                  }

                  @Override
                  public int getValueCount() {
                    return BASE + 1;
                  }

                  @Override
                  public boolean advanceExact(int target) throws IOException {
                    return in.advanceExact(target);
                  }

                  @Override
                  public int docID() {
                    return in.docID();
                  }

                  @Override
                  public int nextDoc() throws IOException {
                    return in.nextDoc();
                  }

                  @Override
                  public int advance(int target) throws IOException {
                    return in.advance(target);
                  }

                  @Override
                  public long cost() {
                    return in.cost();
                  }
                };
              }

              @Override
              public CacheHelper getCoreCacheHelper() {
                return null;
              }

              @Override
              public CacheHelper getReaderCacheHelper() {
                return null;
              }
            };
        FunctionRangeQuery q =
            new FunctionRangeQuery(
                new BytesRefFieldSource("f"), "016777217", null, true, true);
        // doc "016777216" (ord 2^24 of 2^24+1 terms) < "016777217": expected 0 hits, actual 1
        assertEquals(q.toString(), 0, new IndexSearcher(leaf).count(q));
      }
    }
  }
}
