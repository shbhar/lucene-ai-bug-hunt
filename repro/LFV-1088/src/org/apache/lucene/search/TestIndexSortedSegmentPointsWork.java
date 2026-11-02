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
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FilterLeafReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.index.PointValues;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.AssertingLeafReader.AssertingPointValues;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestIndexSortedSegmentPointsWork extends LuceneTestCase {

  public void testEarlyTerminatedSegmentReadsNoPoints() throws IOException {
    Sort sort = new Sort(new SortField("f", SortField.Type.LONG));
    try (Directory unsortedDir = newDirectory();
        Directory sortedDir = newDirectory()) {
      writeIndex(unsortedDir, null, 2000, 100);
      writeIndex(sortedDir, sort, 4096, 0);
      try (DirectoryReader unsorted = DirectoryReader.open(unsortedDir);
          DirectoryReader sorted = DirectoryReader.open(sortedDir)) {
        int[] pointTreeReads = new int[1];
        LeafReader sortedLeaf =
            new FilterLeafReader(getOnlyLeafReader(sorted)) {
              @Override
              public PointValues getPointValues(String field) {
                return new AssertingPointValues(super.getPointValues(field), maxDoc()) {
                  @Override
                  public PointTree getPointTree() throws IOException {
                    pointTreeReads[0]++;
                    return super.getPointTree();
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
        try (IndexReader reader =
            new MultiReader(new IndexReader[] {getOnlyLeafReader(unsorted), sortedLeaf}, false)) {
          new IndexSearcher(reader).search(MatchAllDocsQuery.INSTANCE, 10, sort);
          // expected 0: the index-sorted segment terminates early; actual on main: 2 BKD tree reads
          assertEquals(0, pointTreeReads[0]);
        }
      }
    }
  }

  private static void writeIndex(Directory dir, Sort indexSort, int numDocs, long firstValue)
      throws IOException {
    IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
    if (indexSort != null) {
      iwc.setIndexSort(indexSort);
    }
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < numDocs; i++) {
        Document doc = new Document();
        doc.add(new LongPoint("f", firstValue + i));
        doc.add(new NumericDocValuesField("f", firstValue + i));
        w.addDocument(doc);
      }
    }
  }
}
