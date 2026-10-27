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
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopFieldCollector;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.LuceneTestCase;

/**
 * (Variant for 8.x releases: test-framework package and collector API of 8.x.)
 * An INT index sort on a NumericDocValuesField whose value does not fit in an int. The flush-time
 * sorter (IndexSorter.IntSorter#getDocComparator) and the search-time IntComparator both use
 * (int) longValue(); the merge-time sorter (IntSorter#getComparableProviders) compares the full
 * long. A merge therefore writes a segment that is not in the declared index sort order.
 */
public class TestIntIndexSortMergeOrder extends LuceneTestCase {

  private static final Sort SORT = new Sort(new SortField("s", SortField.Type.INT));

  private static Document doc(String id, long value) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    d.add(new NumericDocValuesField("s", value));
    return d;
  }

  /**
   * Index a (INT sort value (int) 2^32 = 0), b (1) and c (2): a alone in the first segment, b and c
   * in the second, or all three in one segment.
   */
  private static Directory index(boolean oneSegment, boolean merge) throws Exception {
    Directory dir = new ByteBuffersDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig();
    iwc.setIndexSort(SORT);
    iwc.setMergePolicy(new LogDocMergePolicy());
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      w.addDocument(doc("a", 1L << 32));
      if (oneSegment == false) {
        w.commit();
      }
      w.addDocument(doc("b", 1));
      w.addDocument(doc("c", 2));
      w.commit();
      if (merge) {
        w.forceMerge(1);
        w.commit();
      }
    }
    return dir;
  }

  private static String order(Directory dir) throws Exception {
    StringBuilder sb = new StringBuilder();
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      for (LeafReaderContext ctx : r.leaves()) {
        for (int i = 0; i < ctx.reader().maxDoc(); i++) {
          sb.append(ctx.reader().document(i).get("id"));
        }
        sb.append('|');
      }
    }
    return sb.toString();
  }

  private static String firstHit(Directory dir) throws Exception {
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      // totalHitsThreshold 1 lets the collector stop early on the index-sorted segment
      TopFieldCollector c = TopFieldCollector.create(SORT, 1, null, 1);
      new IndexSearcher(r).search(new MatchAllDocsQuery(), c);
      TopDocs td = c.topDocs();
      return r.document(td.scoreDocs[0].doc).get("id");
    }
  }

  private static boolean checkIndexClean(Directory dir) throws Exception {
    try (CheckIndex ci = new CheckIndex(dir)) {
      return ci.checkIndex().clean;
    }
  }

  /** Control: the flush-time sort (one segment) agrees with the search-time comparator. */
  public void testFlushedSegmentIsSorted() throws Exception {
    try (Directory dir = index(true, false)) {
      assertEquals("abc|", order(dir));
      assertTrue(checkIndexClean(dir));
      assertEquals("a", firstHit(dir));
    }
  }

  /** Control: two unmerged segments. */
  public void testUnmergedSegments() throws Exception {
    try (Directory dir = index(false, false)) {
      assertTrue(checkIndexClean(dir));
      assertEquals("a", firstHit(dir));
    }
  }

  /** The merged segment must still be sorted by the index sort (a=0, b=1, c=2). */
  public void testMergedSegmentOrder() throws Exception {
    try (Directory dir = index(false, true)) {
      assertEquals("abc|", order(dir));
    }
  }

  /** CheckIndex must find the merged segment sorted. */
  public void testMergedSegmentPassesCheckIndex() throws Exception {
    try (Directory dir = index(false, true)) {
      assertTrue("CheckIndex reports the merged segment out of index-sort order", checkIndexClean(dir));
    }
  }

  /** A sorted search on the merged index must return the same top hit as before the merge. */
  public void testMergedSegmentSortedSearch() throws Exception {
    try (Directory dir = index(false, true)) {
      assertEquals("a", firstHit(dir));
    }
  }
}
