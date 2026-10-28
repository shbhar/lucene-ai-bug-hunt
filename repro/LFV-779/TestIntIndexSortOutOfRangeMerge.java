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
import org.apache.lucene.tests.util.TestUtil;

/**
 * An index sorted by an INT sort field, whose numeric doc values hold a long outside the int range.
 *
 * <p>Documents: a = 3_000_000_000L ((int) = -1_294_967_296), b = -5, c = 7. Flush-time sorting and
 * the search-time INT comparator both use (int) longValue(), so the INT order is a, b, c. The test
 * asserts that a merged segment keeps that order, as a flushed segment does.
 */
public class TestIntIndexSortOutOfRangeMerge extends LuceneTestCase {

  private static final String F = "s";
  private static final long A = 3_000_000_000L; // does not fit in an int: (int) A < -5
  private static final long B = -5L;
  private static final long C = 7L;

  private static Document doc(String id, long v, boolean sortedNumeric) {
    Document d = new Document();
    d.add(new StoredField("id", id));
    d.add(sortedNumeric ? new SortedNumericDocValuesField(F, v) : new NumericDocValuesField(F, v));
    return d;
  }

  private static IndexWriter writer(Directory dir, Sort sort) throws IOException {
    IndexWriterConfig iwc = new IndexWriterConfig();
    iwc.setCodec(TestUtil.getDefaultCodec());
    iwc.setIndexSort(sort);
    iwc.setMergeScheduler(new SerialMergeScheduler());
    iwc.setMaxBufferedDocs(100);
    return new IndexWriter(dir, iwc);
  }

  /** Segment 1 = [a], segment 2 = [b, c]; optionally forceMerge(1). */
  private static Directory twoSegments(Sort sort, boolean sortedNumeric, long a, boolean merge)
      throws IOException {
    Directory dir = new ByteBuffersDirectory();
    try (IndexWriter w = writer(dir, sort)) {
      w.addDocument(doc("a", a, sortedNumeric));
      w.commit();
      w.addDocument(doc("b", B, sortedNumeric));
      w.addDocument(doc("c", C, sortedNumeric));
      w.commit();
      if (merge) {
        w.forceMerge(1);
        w.commit();
      }
    }
    return dir;
  }

  /** Concatenated ids in docID order, one '|' after each segment. */
  private static String order(DirectoryReader r) throws IOException {
    StringBuilder sb = new StringBuilder();
    for (LeafReaderContext ctx : r.leaves()) {
      StoredFields sf = ctx.reader().storedFields();
      for (int i = 0; i < ctx.reader().maxDoc(); i++) {
        sb.append(sf.document(i).get("id"));
      }
      sb.append('|');
    }
    return sb.toString();
  }

  /** CheckIndex's own index-sort test on every segment; returns null when all are in order. */
  private static String checkIndexSortErrors(DirectoryReader r, Sort sort) throws IOException {
    for (LeafReaderContext ctx : r.leaves()) {
      CheckIndex.Status.IndexSortStatus st =
          CheckIndex.testSort((CodecReader) ctx.reader(), sort, null, false);
      if (st.error != null) {
        return st.error.toString();
      }
    }
    return null;
  }

  private static String topId(IndexSearcher s, Sort sort, int totalHitsThreshold)
      throws IOException {
    TopDocs td =
        s.search(
            new MatchAllDocsQuery(), new TopFieldCollectorManager(sort, 1, null, totalHitsThreshold));
    return s.storedFields().document(td.scoreDocs[0].doc).get("id");
  }

  // ---- controls -------------------------------------------------------------------------

  /** Control: the same three docs flushed into ONE segment are sorted by (int) value: a, b, c. */
  public void testControlFlushedSegmentUsesIntOrder() throws IOException {
    Sort sort = new Sort(new SortField(F, SortField.Type.INT));
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = writer(dir, sort)) {
        w.addDocument(doc("c", C, false));
        w.addDocument(doc("b", B, false));
        w.addDocument(doc("a", A, false));
        w.commit();
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals("abc|", order(r));
        assertNull(checkIndexSortErrors(r, sort));
      }
    }
  }

  /** Control: an in-range value equal to (int) A merges into the expected order a, b, c. */
  public void testControlInRangeValueMergesCorrectly() throws IOException {
    Sort sort = new Sort(new SortField(F, SortField.Type.INT));
    try (Directory dir = twoSegments(sort, false, (int) A, true);
        DirectoryReader r = DirectoryReader.open(dir)) {
      assertEquals("abc|", order(r));
      assertNull(checkIndexSortErrors(r, sort));
    }
  }

  /** Control: before the merge, both segments are in order and both searches agree on "a". */
  public void testControlUnmergedIndexIsConsistent() throws IOException {
    Sort sort = new Sort(new SortField(F, SortField.Type.INT));
    try (Directory dir = twoSegments(sort, false, A, false);
        DirectoryReader r = DirectoryReader.open(dir)) {
      assertEquals("a|bc|", order(r));
      assertNull(checkIndexSortErrors(r, sort));
      IndexSearcher s = new IndexSearcher(r);
      Sort notPrefix = new Sort(new SortField(F, SortField.Type.INT), SortField.FIELD_DOC);
      assertEquals("a", topId(s, notPrefix, Integer.MAX_VALUE));
      assertEquals("a", topId(s, sort, Integer.MAX_VALUE));
      assertEquals("a", topId(s, sort, 1));
    }
  }

  // ---- the claim ------------------------------------------------------------------------

  /** The merged segment must follow the INT index sort, like the flushed segment does. */
  public void testMergedSegmentOrder() throws IOException {
    Sort sort = new Sort(new SortField(F, SortField.Type.INT));
    try (Directory dir = twoSegments(sort, false, A, true);
        DirectoryReader r = DirectoryReader.open(dir)) {
      assertEquals(sort, SegmentInfos.readLatestCommit(dir).info(0).info.getIndexSort());
      assertEquals("merged order", "abc|", order(r));
    }
  }

  /** Same, reverse sort: INT order is c, b, a. */
  public void testMergedSegmentOrderReverse() throws IOException {
    Sort sort = new Sort(new SortField(F, SortField.Type.INT, true));
    try (Directory dir = twoSegments(sort, false, A, true);
        DirectoryReader r = DirectoryReader.open(dir)) {
      assertEquals("merged order", "cba|", order(r));
    }
  }

  /** CheckIndex's index-sort test must accept the segment the merge wrote. */
  public void testMergedSegmentPassesCheckIndexSortTest() throws IOException {
    Sort sort = new Sort(new SortField(F, SortField.Type.INT));
    try (Directory dir = twoSegments(sort, false, A, true);
        DirectoryReader r = DirectoryReader.open(dir)) {
      assertNull(checkIndexSortErrors(r, sort));
    }
  }

  /**
   * Oracle: INT then doc-id tie-break is not a prefix of the index sort, so the collector compares
   * every document with the INT comparator and cannot rely on the segment order. Its top hit is the
   * INT minimum. A search by the index sort itself (which may rely on the segment order) must return
   * the same top hit, with any totalHitsThreshold.
   */
  public void testSortedSearchAfterMergeMatchesComparator() throws IOException {
    Sort sort = new Sort(new SortField(F, SortField.Type.INT));
    Sort notPrefix = new Sort(new SortField(F, SortField.Type.INT), SortField.FIELD_DOC);
    try (Directory dir = twoSegments(sort, false, A, true);
        DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = new IndexSearcher(r);
      String oracle = topId(s, notPrefix, Integer.MAX_VALUE);
      assertEquals("INT comparator top hit (non-prefix sort)", "a", oracle);
      assertEquals("index-sort search, threshold MAX_VALUE", oracle, topId(s, sort, Integer.MAX_VALUE));
      assertEquals("index-sort search, threshold 1", oracle, topId(s, sort, 1));
    }
  }

  /** Same claim through SortedNumericSortField with Type.INT. */
  public void testSortedNumericIntMergedSegmentOrder() throws IOException {
    Sort sort = new Sort(new SortedNumericSortField(F, SortField.Type.INT));
    try (Directory dir = twoSegments(sort, true, A, true);
        DirectoryReader r = DirectoryReader.open(dir)) {
      assertEquals("merged order", "abc|", order(r));
    }
  }
}
