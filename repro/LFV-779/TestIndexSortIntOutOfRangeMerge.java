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

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.SortedNumericSortField;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopFieldCollectorManager;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * An INT index sort over a doc value that does not fit in an int. Flush, the INT search comparator
 * and CheckIndex order such a value by its low 32 bits; a merge must order it the same way.
 */
public class TestIndexSortIntOutOfRangeMerge extends LuceneTestCase {

  /** 2^32 does not fit in an int. Its INT sort value, (int) 2^32, is 0. */
  private static final long A = 1L << 32;

  private static final Sort INT_SORT = new Sort(new SortField("s", SortField.Type.INT));

  private static Document doc(String id, long value, boolean sortedNumeric) {
    Document doc = new Document();
    doc.add(new StoredField("id", id));
    if (sortedNumeric) {
      doc.add(new SortedNumericDocValuesField("s", value));
    } else {
      doc.add(new NumericDocValuesField("s", value));
    }
    return doc;
  }

  /** Segment 1 holds a, segment 2 holds b = 1 and c = 2; then forceMerge(1). */
  private static Directory mergedIndex(Sort sort, long a, boolean sortedNumeric)
      throws IOException {
    // Not MockDirectoryWrapper: it runs CheckIndex on close, which would hide the assertions below.
    Directory dir = new ByteBuffersDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig();
    iwc.setIndexSort(sort);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      w.addDocument(doc("a", a, sortedNumeric));
      w.commit();
      w.addDocument(doc("b", 1, sortedNumeric));
      w.addDocument(doc("c", 2, sortedNumeric));
      w.commit();
      w.forceMerge(1);
    }
    return dir;
  }

  /** The ids in doc ID order, one '|' after each segment. */
  private static String order(Directory dir) throws IOException {
    StringBuilder sb = new StringBuilder();
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      for (LeafReaderContext ctx : r.leaves()) {
        StoredFields storedFields = ctx.reader().storedFields();
        for (int doc = 0; doc < ctx.reader().maxDoc(); doc++) {
          sb.append(storedFields.document(doc).get("id"));
        }
        sb.append('|');
      }
    }
    return sb.toString();
  }

  /** Control: the same three documents flushed into one segment are in INT order a, b, c. */
  public void testFlushedSegmentOrder() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig();
      iwc.setIndexSort(INT_SORT);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        w.addDocument(doc("c", 2, false));
        w.addDocument(doc("b", 1, false));
        w.addDocument(doc("a", A, false));
      }
      assertEquals("abc|", order(dir));
    }
  }

  /** Control: with an in-range value for a, the merged segment is in order. */
  public void testInRangeValueMerge() throws IOException {
    try (Directory dir = mergedIndex(INT_SORT, 0, false)) {
      assertEquals("abc|", order(dir));
    }
  }

  public void testMergedSegmentOrder() throws IOException {
    try (Directory dir = mergedIndex(INT_SORT, A, false)) {
      assertEquals("abc|", order(dir));
    }
  }

  public void testMergedSegmentOrderReverse() throws IOException {
    Sort reverse = new Sort(new SortField("s", SortField.Type.INT, true));
    try (Directory dir = mergedIndex(reverse, A, false)) {
      assertEquals("cba|", order(dir));
    }
  }

  public void testMergedSegmentOrderSortedNumeric() throws IOException {
    Sort sort = new Sort(new SortedNumericSortField("s", SortField.Type.INT));
    try (Directory dir = mergedIndex(sort, A, true)) {
      assertEquals("abc|", order(dir));
    }
  }

  /** CheckIndex at level 2 runs the index-sort test; the merged segment must pass it. */
  public void testMergedSegmentPassesCheckIndex() throws IOException {
    try (Directory dir = mergedIndex(INT_SORT, A, false);
        CheckIndex checker = new CheckIndex(dir)) {
      checker.setLevel(CheckIndex.Level.MIN_LEVEL_FOR_INTEGRITY_CHECKS);
      CheckIndex.Status status = checker.checkIndex();
      assertTrue("CheckIndex: " + status.segmentInfos.get(0).indexSortStatus.error, status.clean);
    }
  }

  /** A search sorted by the index sort must return the INT minimum, a, first. */
  public void testSortedSearchTopHit() throws IOException {
    try (Directory dir = mergedIndex(INT_SORT, A, false);
        DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(r);
      TopDocs top =
          searcher.search(
              new MatchAllDocsQuery(),
              new TopFieldCollectorManager(INT_SORT, 1, null, Integer.MAX_VALUE));
      assertEquals("a", searcher.storedFields().document(top.scoreDocs[0].doc).get("id"));
    }
  }
}
