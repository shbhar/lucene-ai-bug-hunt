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

import org.apache.lucene.document.LongPoint;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestSegmentOrderNullCoreCacheHelper extends LuceneTestCase {

  public void testReorderLeavesWithoutCoreCacheHelper() throws Exception {
    MemoryIndex low = new MemoryIndex();
    low.addField(new LongPoint("f", 1), null);
    MemoryIndex high = new MemoryIndex();
    high.addField(new LongPoint("f", 2), null);
    IndexReader highReader = high.createSearcher().getIndexReader();
    try (MultiReader multi =
        new MultiReader(low.createSearcher().getIndexReader(), highReader)) {
      Sort sort = new Sort(new SortField("f", SortField.Type.LONG, true));
      IndexReader reordered;
      // expected leaves ordered 2, 1; actual NPE (MemoryIndex leaves have a null core cache helper)
      try {
        reordered = SegmentOrder.fromSort(sort).reorder(multi);
      } catch (NullPointerException e) {
        throw new AssertionError("expected the leaves ordered by f descending (2, 1), got " + e, e);
      }
      assertSame(highReader, reordered.leaves().get(0).reader());
    }
  }
}
