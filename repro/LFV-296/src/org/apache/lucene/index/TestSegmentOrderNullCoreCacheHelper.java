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
package org.apache.lucene.index;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * LeafReader#getCoreCacheHelper may return null ("not suited for caching"), as ParallelLeafReader
 * does when it combines more than one reader. SegmentOrder's numeric comparator dereferences the
 * core cache helper unconditionally, so re-ordering such a reader throws NullPointerException.
 */
public class TestSegmentOrderNullCoreCacheHelper extends LuceneTestCase {

  /** Two indexes with the same segment structure: 2 segments of 3 docs each. */
  private static void build(Directory dir, boolean points) throws Exception {
    IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int seg = 0; seg < 2; seg++) {
        for (int i = 0; i < 3; i++) {
          Document doc = new Document();
          if (points) {
            doc.add(new LongPoint("f", seg * 100L + i));
          } else {
            doc.add(new StoredField("other", i));
          }
          w.addDocument(doc);
        }
        w.commit();
      }
    }
  }

  public void testReorderParallelReader() throws Exception {
    try (Directory d1 = newDirectory();
        Directory d2 = newDirectory()) {
      build(d1, true);
      build(d2, false);
      try (DirectoryReader r1 = DirectoryReader.open(d1);
          DirectoryReader r2 = DirectoryReader.open(d2);
          ParallelCompositeReader parallel = new ParallelCompositeReader(false, r1, r2)) {
        assertEquals(2, parallel.leaves().size());
        // legal per LeafReader#getCoreCacheHelper: null means "not suited for caching"
        assertNull(parallel.leaves().get(0).reader().getCoreCacheHelper());

        Sort sort = new Sort(new SortField("f", SortField.Type.LONG, true));
        IndexReader reordered = SegmentOrder.fromSort(sort).reorder(parallel);
        // descending sort on f: the segment holding 100..102 must come first
        assertEquals(2, reordered.leaves().size());
        assertSame(parallel.leaves().get(1).reader(), reordered.leaves().get(0).reader());
      }
    }
  }
}
