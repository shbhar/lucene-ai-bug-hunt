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
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocValues;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SortFieldProvider;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.DataInput;
import org.apache.lucene.store.DataOutput;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

/**
 * BinarySortField documents that a subclass may derive the sort key "from the stored bytes and/or
 * other doc-values fields of the segment". Here the key comes from the doc-values field "a" only,
 * under the sort-field name "key", which no document indexes. The index is sorted by (key, f), so
 * documents are in (a, f) order, but Sort.getPrimarySortField skips "key" ("no values in this
 * segment") and returns f, and the doc-values range query on f uses its index-sort shortcut.
 *
 * <p>Needs META-INF/services/org.apache.lucene.index.SortFieldProvider listing {@link
 * DerivedKeySortField.Provider} on the classpath (as BinarySortField requires for subclasses).
 */
public class TestPrimarySortFieldDerivedBinaryKey extends LuceneTestCase {

  /** Sort key = the 8-byte sortable encoding of the NUMERIC field "a". */
  public static final class DerivedKeySortField extends BinarySortField {
    static final String NAME = "TestDerivedKeySortField";

    public DerivedKeySortField(String field) {
      super(field, false, null, NAME);
    }

    @Override
    protected BinaryDocValues getSortKeyDocValues(LeafReader reader) throws IOException {
      NumericDocValues a = DocValues.getNumeric(reader, "a");
      BytesRef key = new BytesRef(new byte[Long.BYTES]);
      return new BinaryDocValues() {
        @Override
        public BytesRef binaryValue() throws IOException {
          long v = a.longValue() ^ Long.MIN_VALUE;
          for (int i = 0; i < Long.BYTES; i++) {
            key.bytes[i] = (byte) (v >>> (56 - 8 * i));
          }
          return key;
        }

        @Override
        public int docID() {
          return a.docID();
        }

        @Override
        public int nextDoc() throws IOException {
          return a.nextDoc();
        }

        @Override
        public int advance(int target) throws IOException {
          return a.advance(target);
        }

        @Override
        public boolean advanceExact(int target) throws IOException {
          return a.advanceExact(target);
        }

        @Override
        public long cost() {
          return a.cost();
        }
      };
    }

    /** Serializes/deserializes a {@link DerivedKeySortField}. */
    public static final class Provider extends SortFieldProvider {
      public Provider() {
        super(NAME);
      }

      @Override
      public SortField readSortField(DataInput in) throws IOException {
        return new DerivedKeySortField(in.readString());
      }

      @Override
      public void writeSortField(SortField sf, DataOutput out) throws IOException {
        out.writeString(sf.getField());
      }
    }
  }

  /**
   * Sort key = (own NUMERIC field, companion NUMERIC field "b"), both as 8-byte sortable bytes. The
   * own field has a skip index; when it is constant in a segment, getPrimarySortField's skipper rule
   * skips this sort field although the documents are still ordered by "b".
   */
  public static final class CompositeKeySortField extends BinarySortField {
    static final String NAME = "TestCompositeKeySortField";

    public CompositeKeySortField(String field) {
      super(field, false, null, NAME);
    }

    @Override
    protected BinaryDocValues getSortKeyDocValues(LeafReader reader) throws IOException {
      NumericDocValues own = DocValues.getNumeric(reader, getField());
      NumericDocValues b = DocValues.getNumeric(reader, "b");
      BytesRef key = new BytesRef(new byte[2 * Long.BYTES]);
      return new BinaryDocValues() {
        @Override
        public BytesRef binaryValue() throws IOException {
          while (b.docID() < own.docID()) {
            b.nextDoc();
          }
          long[] parts = {own.longValue(), b.docID() == own.docID() ? b.longValue() : 0};
          for (int p = 0; p < 2; p++) {
            long v = parts[p] ^ Long.MIN_VALUE;
            for (int i = 0; i < Long.BYTES; i++) {
              key.bytes[p * Long.BYTES + i] = (byte) (v >>> (56 - 8 * i));
            }
          }
          return key;
        }

        @Override
        public int docID() {
          return own.docID();
        }

        @Override
        public int nextDoc() throws IOException {
          return own.nextDoc();
        }

        @Override
        public int advance(int target) throws IOException {
          return own.advance(target);
        }

        @Override
        public boolean advanceExact(int target) throws IOException {
          return own.advanceExact(target);
        }

        @Override
        public long cost() {
          return own.cost();
        }
      };
    }

    /** Serializes/deserializes a {@link CompositeKeySortField}. */
    public static final class Provider extends SortFieldProvider {
      public Provider() {
        super(NAME);
      }

      @Override
      public SortField readSortField(DataInput in) throws IOException {
        return new CompositeKeySortField(in.readString());
      }

      @Override
      public void writeSortField(SortField sf, DataOutput out) throws IOException {
        out.writeString(sf.getField());
      }
    }
  }

  // (a, f) pairs; sorted by (a, f) the docs hold f = 5, 9, 1, 7, 3
  private static final long[][] DOCS = {{1, 7}, {0, 9}, {2, 3}, {0, 5}, {1, 1}};

  public void testRangeOnSecondSortField() throws IOException {
    Sort sort =
        new Sort(new DerivedKeySortField("key"), new SortField("f", SortField.Type.LONG));
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()).setIndexSort(sort);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (long[] af : DOCS) {
          Document doc = new Document();
          doc.add(new NumericDocValuesField("a", af[0]));
          doc.add(NumericDocValuesField.indexedField("f", af[1]));
          w.addDocument(doc);
        }
        w.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(1, reader.leaves().size());
        // the documents really are in (a, f) order
        NumericDocValues f = reader.leaves().get(0).reader().getNumericDocValues("f");
        long[] order = new long[DOCS.length];
        for (int i = 0; i < order.length; i++) {
          assertTrue(f.advanceExact(i));
          order[i] = f.longValue();
        }
        assertArrayEquals(new long[] {5, 9, 1, 7, 3}, order);

        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setQueryCache(null);
        Query q = NumericDocValuesField.newSlowRangeQuery("f", 1, 3);
        assertEquals("search(), " + q, 2, searcher.search(q, 10).totalHits.value());
        assertEquals("count(), " + q, 2, searcher.count(q));
      }
    }
  }

  /** Same, through the skipper rule: the key's own field "a" is constant (skip-indexed), "b" is not. */
  public void testRangeOnSecondSortFieldCompositeKey() throws IOException {
    Sort sort =
        new Sort(new CompositeKeySortField("a"), new SortField("f", SortField.Type.LONG));
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()).setIndexSort(sort);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (long[] bf : DOCS) {
          Document doc = new Document();
          doc.add(NumericDocValuesField.indexedField("a", 42));
          doc.add(new NumericDocValuesField("b", bf[0]));
          doc.add(NumericDocValuesField.indexedField("f", bf[1]));
          w.addDocument(doc);
        }
        w.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(1, reader.leaves().size());
        NumericDocValues f = reader.leaves().get(0).reader().getNumericDocValues("f");
        long[] order = new long[DOCS.length];
        for (int i = 0; i < order.length; i++) {
          assertTrue(f.advanceExact(i));
          order[i] = f.longValue();
        }
        assertArrayEquals(new long[] {5, 9, 1, 7, 3}, order);
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setQueryCache(null);
        Query q = NumericDocValuesField.newSlowRangeQuery("f", 1, 3);
        assertEquals("search(), " + q, 2, searcher.search(q, 10).totalHits.value());
        assertEquals("count(), " + q, 2, searcher.count(q));
      }
    }
  }
}
