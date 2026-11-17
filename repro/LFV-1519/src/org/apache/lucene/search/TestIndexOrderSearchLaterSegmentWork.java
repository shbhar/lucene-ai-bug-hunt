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

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FilterLeafReader;
import org.apache.lucene.index.FilterNumericDocValues;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestIndexOrderSearchLaterSegmentWork extends LuceneTestCase {

  // Expected: 0 values read in the second segment (the first gave the top 10 and 1,001 hits, as
  // on 10.6.0). Actual: 1000, every document of the second segment.
  public void testLaterSegmentIsSkippedOnceTopHitsAreCollected() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig config = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter writer = new IndexWriter(dir, config)) {
        for (int i = 0; i < 2001; i++) {
          Document doc = new Document();
          doc.add(new NumericDocValuesField("x", i <= 1000 ? 1 : 0));
          writer.addDocument(doc);
          if (i == 1000) {
            writer.flush();
          }
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        int[] valuesRead = new int[1];
        LeafReader second =
            new FilterLeafReader(reader.leaves().get(1).reader()) {
              @Override
              public NumericDocValues getNumericDocValues(String field) throws IOException {
                return new FilterNumericDocValues(in.getNumericDocValues(field)) {
                  @Override
                  public long longValue() throws IOException {
                    valuesRead[0]++;
                    return in.longValue();
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
        IndexReader[] segments = {reader.leaves().get(0).reader(), second};
        try (IndexReader both = new MultiReader(segments, false)) {
          Query query = NumericDocValuesField.newSlowRangeQuery("x", 1, 1);
          new IndexSearcher(both).search(query, 10, Sort.INDEXORDER);
          assertEquals("values of x read in the second segment", 0, valuesRead[0]);
        }
      }
    }
  }
}
