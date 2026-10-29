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
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.NumericUtils;

/**
 * A doc-values range query on a skip-indexed field must match every document whose value is in the
 * range, also when the values of one segment span more than {@link Long#MAX_VALUE}. Signed doubles
 * stored as {@link NumericUtils#doubleToSortableLong} sortable longs do that as soon as they reach
 * past +/-2 on both sides of zero, for example latitudes.
 */
public class TestSkipIndexedDocValuesRangeWideSpan extends LuceneTestCase {

  public void testLatitudeRangeQuery() throws Exception {
    // 2048 latitudes evenly spaced from pole to pole (exact binary fractions): a quarter of them,
    // 512, lie in [45, 90]. 2048 docs is the smallest segment for which search(q, 10) uses the
    // dense window-by-window bulk scorer; count(q) is already wrong from 257 distinct values on.
    int numDocs = 2048;
    int expected = 0;
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < numDocs; i++) {
          double lat = -90 + (i + 0.5) * (180.0 / numDocs);
          if (lat >= 45 && lat <= 90) {
            expected++;
          }
          Document doc = new Document();
          doc.add(
              NumericDocValuesField.indexedField("lat", NumericUtils.doubleToSortableLong(lat)));
          w.addDocument(doc);
        }
        w.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Query query =
            NumericDocValuesField.newSlowRangeQuery(
                "lat",
                NumericUtils.doubleToSortableLong(45),
                NumericUtils.doubleToSortableLong(90));
        assertEquals(512, expected);
        int count = searcher.count(query);
        int hits = searcher.search(query, 10).scoreDocs.length;
        assertEquals("count(q); search(q, 10) returned " + hits + " hits", expected, count);
        assertEquals("search(q, 10) hits", 10, hits);
      }
    }
  }
}
