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
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestIndexSortIntSortLongBound extends LuceneTestCase {

  public void testOpenLowerBoundOnIntSort() throws Exception {
    IndexWriterConfig iwc = new IndexWriterConfig();
    iwc.setIndexSort(new Sort(new SortField("f", SortField.Type.INT)));
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int v : new int[] {-5, 3, 20}) {
        Document doc = new Document();
        doc.add(new NumericDocValuesField("f", v));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Query fallback = NumericDocValuesField.newSlowRangeQuery("f", Long.MIN_VALUE, 10);
        Query query =
            new IndexSortSortedNumericDocValuesRangeQuery("f", Long.MIN_VALUE, 10, fallback);
        assertEquals(2, searcher.search(fallback, 10).scoreDocs.length);
        // expected 2 hits (-5 and 3) like the fallback, actual 1: -5 is missed
        assertEquals(2, searcher.search(query, 10).scoreDocs.length);
      }
    }
  }
}
