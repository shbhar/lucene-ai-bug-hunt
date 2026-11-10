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
package org.apache.lucene.sandbox.search;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestNumericMultiRangeHalfOpenClause extends LuceneTestCase {

  public void testHalfOpenClause() throws Exception {
    try (Directory dir = newDirectory();
        RandomIndexWriter w = new RandomIndexWriter(random(), dir)) {
      for (long v : new long[] {5, 150}) {
        Document doc = new Document();
        doc.add(new SortedNumericDocValuesField("f", v));
        w.addDocument(doc);
      }
      try (IndexReader reader = w.getReader()) {
        // expected 2 hits (f <= 10 OR 100 <= f <= 200), actual 1: [Long.MIN_VALUE, 10] is dropped as reversed
        Query query =
            new DocValuesMultiRangeQuery.SortedNumericStabbingBuilder("f")
                .add(Long.MIN_VALUE, 10)
                .add(100, 200)
                .build();
        assertEquals(2, newSearcher(reader).count(query));
      }
    }
  }
}
