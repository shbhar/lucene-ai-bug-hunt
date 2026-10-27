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
package columnverify;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.column.BinaryColumn;
import org.apache.lucene.document.column.BytesRefValuesCursor;
import org.apache.lucene.document.column.Column;
import org.apache.lucene.document.column.ColumnBatch;
import org.apache.lucene.document.column.LongColumn;
import org.apache.lucene.document.column.LongTupleCursor;
import org.apache.lucene.document.column.LongValuesCursor;
import org.apache.lucene.document.column.ObjectTupleCursor;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.TieredMergePolicy;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.PointValues;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.NumericUtils;

/**
 * IndexWriter#addBatch with a DENSE points column. If the batch fails part-way through the points
 * column (a caller cursor that throws, or a value that Lucene itself rejects for its length), the
 * exception is non-aborting: the batch's documents are deleted and the writer stays open. The
 * documents indexed afterwards must keep their own point values.
 *
 * <p>Only public API is used (this class is deliberately in a non-Lucene package).
 */
public class TestDensePointsPartialFailure extends LuceneTestCase {

  private static final int ID_BASE = 0;

  // ---------------------------------------------------------------------------------------------
  // Tests
  // ---------------------------------------------------------------------------------------------

  /** 4-byte int points (LongColumn INT); the cursor throws after the first 1024-value chunk. */
  public void testIntPointsCursorThrowsAfterFirstChunk() throws IOException {
    runIntScenario(1500, 1200, true);
  }

  /** Control: same batch, but the cursor throws inside the first chunk. */
  public void testControlIntPointsCursorThrowsInsideFirstChunk() throws IOException {
    runIntScenario(1500, 500, true);
  }

  /** Control: failure after the first chunk, but a commit separates the failure from later docs. */
  public void testControlIntPointsCommitBetween() throws IOException {
    runIntScenario(1500, 1200, false);
  }

  /** 8-byte long points (LongColumn LONG); the cursor throws after the first 512-value chunk. */
  public void testLongPointsCursorThrowsAfterFirstChunk() throws IOException {
    Directory dir = newDirectory();
    IndexWriter w = new IndexWriter(dir, config());
    FieldType pointType = pointType(1, Long.BYTES);

    addLongDoc(w, 0, 11L);
    addLongDoc(w, 1, 12L);

    int n = 600;
    long[] batchValues = new long[n];
    for (int i = 0; i < n; i++) {
      batchValues[i] = 5_000_000_000L + i;
    }
    IllegalStateException boom = new IllegalStateException("cursor failure");
    RuntimeException thrown =
        expectThrows(
            RuntimeException.class,
            () ->
                w.addBatch(
                    batch(
                        n,
                        new DenseLongCol(
                            "p", pointType, LongColumn.NumericKind.LONG, batchValues, 550, boom))));
    assertSame(boom, thrown);
    assertTrue(w.isOpen());
    assertNull(w.getTragicException());

    addLongDoc(w, 2, 17L);
    addLongDoc(w, 3, 18L);
    addLongDoc(w, 4, 19L);

    try (DirectoryReader r = DirectoryReader.open(w)) {
      TreeMap<Long, List<Long>> expected = new TreeMap<>();
      expected.put(0L, List.of(11L));
      expected.put(1L, List.of(12L));
      expected.put(2L, List.of(17L));
      expected.put(3L, List.of(18L));
      expected.put(4L, List.of(19L));
      assertEquals(expected, dumpPoints1D(r, "p", Long.BYTES));
      IndexSearcher s = newSearcher(r);
      assertEquals(1, s.count(LongPoint.newExactQuery("p", 17L)));
      assertEquals(0, s.count(LongPoint.newExactQuery("p", 5_000_000_000L)));
    }
    w.close();
    dir.close();
  }

  /**
   * 2-dimension int points via a dense BinaryColumn (8 bytes per point, 512 per chunk). Value 600
   * has the wrong length and is rejected by the default BytesRefValuesCursor#fillPackedPoints.
   */
  public void testBinaryPointsWrongLengthRejectedAfterFirstChunk() throws IOException {
    Directory dir = newDirectory();
    IndexWriter w = new IndexWriter(dir, config());
    FieldType pointType = pointType(2, Integer.BYTES);

    addInt2DDoc(w, 0, 1, 1);

    int n = 700;
    BytesRef[] batchValues = new BytesRef[n];
    for (int i = 0; i < n; i++) {
      batchValues[i] = IntPoint.pack(900_000 + i, -900_000 - i);
    }
    batchValues[600] = new BytesRef(new byte[5]); // not 2 * 4 bytes
    IllegalArgumentException e =
        expectThrows(
            IllegalArgumentException.class,
            () -> w.addBatch(batch(n, new DenseBinaryCol("q", pointType, batchValues))));
    assertTrue(e.getMessage(), e.getMessage().contains("length=5"));
    assertTrue(w.isOpen());
    assertNull(w.getTragicException());

    addInt2DDoc(w, 1, 3, 4);
    addInt2DDoc(w, 2, 5, 6);

    try (DirectoryReader r = DirectoryReader.open(w)) {
      TreeMap<Long, List<String>> expected = new TreeMap<>();
      expected.put(0L, List.of("[1, 1]"));
      expected.put(1L, List.of("[3, 4]"));
      expected.put(2L, List.of("[5, 6]"));
      assertEquals(expected, dumpPoints2DInt(r, "q"));
      IndexSearcher s = newSearcher(r);
      assertEquals(
          1, s.count(IntPoint.newRangeQuery("q", new int[] {3, 4}, new int[] {3, 4})));
      assertEquals(
          0,
          s.count(
              IntPoint.newRangeQuery(
                  "q",
                  new int[] {900_000, Integer.MIN_VALUE},
                  new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE})));
    }
    w.close();
    dir.close();
  }

  // ---------------------------------------------------------------------------------------------
  // Scenario driver
  // ---------------------------------------------------------------------------------------------

  private void runIntScenario(int n, int throwAt, boolean sameSegment) throws IOException {
    Directory dir = newDirectory();
    IndexWriter w = new IndexWriter(dir, config());
    FieldType pointType = pointType(1, Integer.BYTES);

    // Two documents before the failing batch, same segment.
    addIntDoc(w, 0, 1);
    addIntDoc(w, 1, 2);

    long[] batchValues = new long[n];
    for (int i = 0; i < n; i++) {
      batchValues[i] = 1_000_000 + i;
    }
    IllegalStateException boom = new IllegalStateException("cursor failure");
    RuntimeException thrown =
        expectThrows(
            RuntimeException.class,
            () ->
                w.addBatch(
                    batch(
                        n,
                        new DenseLongCol(
                            "p", pointType, LongColumn.NumericKind.INT, batchValues, throwAt, boom))));
    assertSame(boom, thrown);
    assertTrue(w.isOpen());
    assertNull(w.getTragicException());

    if (sameSegment == false) {
      w.commit();
    }

    // Three documents after the failing batch.
    addIntDoc(w, 2, 7);
    addIntDoc(w, 3, 8);
    addIntDoc(w, 4, 9);
    w.commit();

    // Record (not assert) what CheckIndex says, then assert on the values.
    String checkIndexOutcome;
    try {
      TestUtil.checkIndex(dir);
      checkIndexOutcome = "clean";
    } catch (Throwable t) {
      checkIndexOutcome = "FAILED: " + t;
    }

    TreeMap<Long, List<Long>> expected = new TreeMap<>();
    expected.put(0L, List.of(1L));
    expected.put(1L, List.of(2L));
    expected.put(2L, List.of(7L));
    expected.put(3L, List.of(8L));
    expected.put(4L, List.of(9L));

    TreeMap<Long, List<Long>> beforeMerge;
    int count7;
    int countBatch;
    try (DirectoryReader r = DirectoryReader.open(w)) {
      beforeMerge = dumpPoints1D(r, "p", Integer.BYTES);
      IndexSearcher s = newSearcher(r);
      count7 = s.count(IntPoint.newExactQuery("p", 7));
      countBatch = s.count(IntPoint.newRangeQuery("p", 1_000_000, 1_000_000 + n));
    }

    // One more segment, then merge everything into one segment.
    addIntDoc(w, 5, 10);
    expected.put(5L, List.of(10L));
    w.forceMerge(1);
    TreeMap<Long, List<Long>> afterMerge;
    try (DirectoryReader r = DirectoryReader.open(w)) {
      assertEquals(1, r.leaves().size());
      afterMerge = dumpPoints1D(r, "p", Integer.BYTES);
    }
    w.close();
    {
      System.out.println(
          "n="
              + n
              + " throwAt="
              + throwAt
              + " sameSegment="
              + sameSegment
              + " checkIndex="
              + checkIndexOutcome
              + " count(p==7)="
              + count7
              + " count(p in failed-batch range)="
              + countBatch
              + "\n  beforeMerge(id->values)="
              + beforeMerge
              + "\n  afterMerge(id->values)="
              + afterMerge);
    }
    dir.close();

    TreeMap<Long, List<Long>> expectedBeforeMerge = new TreeMap<>(expected);
    expectedBeforeMerge.remove(5L);
    assertEquals("point values per live doc id (before merge)", expectedBeforeMerge, beforeMerge);
    assertEquals(1, count7);
    assertEquals(0, countBatch);
    assertEquals("point values per live doc id (after merge)", expected, afterMerge);
  }

  // ---------------------------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------------------------

  private IndexWriterConfig config() {
    // Deterministic flushing: nothing flushes on its own between the failing batch and the next
    // documents, so they share one in-memory segment.
    IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
    iwc.setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH);
    iwc.setRAMBufferSizeMB(256);
    iwc.setMergePolicy(new TieredMergePolicy()); // no natural merges with this few segments
    return iwc;
  }

  private static FieldType pointType(int dims, int bytes) {
    FieldType ft = new FieldType();
    ft.setDimensions(dims, bytes);
    ft.freeze();
    return ft;
  }

  private static void addIntDoc(IndexWriter w, long id, int value) throws IOException {
    Document doc = new Document();
    doc.add(new NumericDocValuesField("id", ID_BASE + id));
    doc.add(new IntPoint("p", value));
    w.addDocument(doc);
  }

  private static void addLongDoc(IndexWriter w, long id, long value) throws IOException {
    Document doc = new Document();
    doc.add(new NumericDocValuesField("id", ID_BASE + id));
    doc.add(new LongPoint("p", value));
    w.addDocument(doc);
  }

  private static void addInt2DDoc(IndexWriter w, long id, int a, int b) throws IOException {
    Document doc = new Document();
    doc.add(new NumericDocValuesField("id", ID_BASE + id));
    doc.add(new IntPoint("q", a, b));
    w.addDocument(doc);
  }

  private static ColumnBatch batch(int numDocs, Column... cols) {
    List<Column> list = List.of(cols);
    return new ColumnBatch() {
      @Override
      public int numDocs() {
        return numDocs;
      }

      @Override
      public Iterable<Column> columns() {
        return list;
      }
    };
  }

  /** Live doc "id" -> point values of that doc, decoded as 1-D int or long. */
  private static TreeMap<Long, List<Long>> dumpPoints1D(DirectoryReader r, String field, int width)
      throws IOException {
    TreeMap<Long, List<Long>> out = new TreeMap<>();
    for (LeafReaderContext ctx : r.leaves()) {
      LeafReader leaf = ctx.reader();
      PointValues pv = leaf.getPointValues(field);
      if (pv == null) {
        continue;
      }
      long[] ids = readIds(leaf);
      Bits live = leaf.getLiveDocs();
      pv.intersect(
          new PointValues.IntersectVisitor() {
            @Override
            public void visit(int docID) {
              throw new AssertionError("CELL_INSIDE_QUERY is never returned");
            }

            @Override
            public void visit(int docID, byte[] packedValue) {
              if (live != null && live.get(docID) == false) {
                return;
              }
              long v =
                  width == Integer.BYTES
                      ? NumericUtils.sortableBytesToInt(packedValue, 0)
                      : NumericUtils.sortableBytesToLong(packedValue, 0);
              out.computeIfAbsent(ids[docID], k -> new ArrayList<>()).add(v);
            }

            @Override
            public PointValues.Relation compare(byte[] minPackedValue, byte[] maxPackedValue) {
              return PointValues.Relation.CELL_CROSSES_QUERY;
            }
          });
    }
    return out;
  }

  /** Live doc "id" -> "[a, b]" for 2-D int points. */
  private static TreeMap<Long, List<String>> dumpPoints2DInt(DirectoryReader r, String field)
      throws IOException {
    TreeMap<Long, List<String>> out = new TreeMap<>();
    for (LeafReaderContext ctx : r.leaves()) {
      LeafReader leaf = ctx.reader();
      PointValues pv = leaf.getPointValues(field);
      if (pv == null) {
        continue;
      }
      long[] ids = readIds(leaf);
      Bits live = leaf.getLiveDocs();
      pv.intersect(
          new PointValues.IntersectVisitor() {
            @Override
            public void visit(int docID) {
              throw new AssertionError("CELL_INSIDE_QUERY is never returned");
            }

            @Override
            public void visit(int docID, byte[] packedValue) {
              if (live != null && live.get(docID) == false) {
                return;
              }
              String v =
                  "["
                      + NumericUtils.sortableBytesToInt(packedValue, 0)
                      + ", "
                      + NumericUtils.sortableBytesToInt(packedValue, Integer.BYTES)
                      + "]";
              out.computeIfAbsent(ids[docID], k -> new ArrayList<>()).add(v);
            }

            @Override
            public PointValues.Relation compare(byte[] minPackedValue, byte[] maxPackedValue) {
              return PointValues.Relation.CELL_CROSSES_QUERY;
            }
          });
    }
    return out;
  }

  private static long[] readIds(LeafReader leaf) throws IOException {
    long[] ids = new long[leaf.maxDoc()];
    java.util.Arrays.fill(ids, -1);
    NumericDocValues dv = leaf.getNumericDocValues("id");
    if (dv != null) {
      for (int d = dv.nextDoc(); d != DocIdSetIterator.NO_MORE_DOCS; d = dv.nextDoc()) {
        ids[d] = dv.longValue();
      }
    }
    return ids;
  }

  // ---------------------------------------------------------------------------------------------
  // Columns
  // ---------------------------------------------------------------------------------------------

  /** Dense LongColumn whose values cursor throws when it reaches index {@code throwAt}. */
  private static final class DenseLongCol extends LongColumn {
    private final long[] values;
    private final int throwAt;
    private final RuntimeException failure;

    DenseLongCol(
        String name,
        FieldType type,
        NumericKind kind,
        long[] values,
        int throwAt,
        RuntimeException failure) {
      super(name, type, Density.DENSE, kind);
      this.values = values;
      this.throwAt = throwAt;
      this.failure = failure;
    }

    @Override
    public LongTupleCursor tuples() {
      return new LongTupleCursor() {
        int pos = -1;

        @Override
        public int nextDoc() {
          pos++;
          if (pos == throwAt) {
            throw failure;
          }
          return pos < values.length ? pos : DocIdSetIterator.NO_MORE_DOCS;
        }

        @Override
        public long longValue() {
          return values[pos];
        }
      };
    }

    @Override
    public LongValuesCursor values() {
      return new LongValuesCursor(values.length) {
        int pos = 0;

        @Override
        public long nextLong() {
          if (pos >= values.length) {
            throw new IllegalStateException("cursor overrun");
          }
          if (pos == throwAt) {
            throw failure;
          }
          return values[pos++];
        }
      };
    }
  }

  /** Dense BinaryColumn using the default (validating) BytesRefValuesCursor#fillPackedPoints. */
  private static final class DenseBinaryCol extends BinaryColumn {
    private final BytesRef[] values;

    DenseBinaryCol(String name, FieldType type, BytesRef[] values) {
      super(name, type, Density.DENSE);
      this.values = values;
    }

    @Override
    public ObjectTupleCursor<BytesRef> tuples() {
      return new ObjectTupleCursor<BytesRef>() {
        int pos = -1;

        @Override
        public int nextDoc() {
          pos++;
          return pos < values.length ? pos : DocIdSetIterator.NO_MORE_DOCS;
        }

        @Override
        public BytesRef value() {
          return values[pos];
        }
      };
    }

    @Override
    public BytesRefValuesCursor values() {
      return new BytesRefValuesCursor(values.length) {
        int pos = 0;

        @Override
        public BytesRef nextValue() {
          if (pos >= values.length) {
            throw new IllegalStateException("cursor overrun");
          }
          return values[pos++];
        }
      };
    }
  }
}
