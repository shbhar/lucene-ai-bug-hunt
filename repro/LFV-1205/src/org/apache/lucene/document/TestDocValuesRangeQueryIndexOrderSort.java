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
package org.apache.lucene.document;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FilterLeafReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafMetaData;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Sort;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

public class TestDocValuesRangeQueryIndexOrderSort extends LuceneTestCase {

  public void testRangeQueryOnIndexOrderSortedLeaf() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w =
            new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
      for (String v : new String[] {"a", "b", "c"}) {
        Document doc = new Document();
        doc.add(SortedDocValuesField.indexedField("f", new BytesRef(v)));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        LeafReader leaf = reader.leaves().get(0).reader();
        LeafMetaData m = leaf.getMetaData();
        LeafMetaData indexOrder =
            new LeafMetaData(m.createdVersionMajor(), m.minVersion(), Sort.INDEXORDER, false);
        LeafReader wrapped =
            new FilterLeafReader(leaf) {
              @Override
              public LeafMetaData getMetaData() {
                return indexOrder;
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
        // Expected: 2 hits (b, c), as on the unwrapped leaf; actual: NullPointerException.
        assertEquals(
            2,
            new IndexSearcher(wrapped)
                .count(
                    SortedDocValuesField.newSlowRangeQuery(
                        "f", new BytesRef("b"), new BytesRef("c"), true, true)));
      }
    }
  }
}
