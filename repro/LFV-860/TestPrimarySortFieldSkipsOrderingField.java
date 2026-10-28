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
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocValues;
import org.apache.lucene.index.DocValuesSkipper;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.FilterLeafReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.PointValues;
import org.apache.lucene.index.SortFieldProvider;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.SortedNumericDocValues;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.index.Terms;
import org.apache.lucene.store.DataInput;
import org.apache.lucene.store.DataOutput;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

/**
 * The index is sorted by two fields, (c, f). In each test the first sort field c really orders the
 * documents, but the reader does not list it in its FieldInfos: either c is a BinarySortField whose
 * key comes from another doc-values field, or a FilterLeafReader hides c. A doc-values range query
 * on f must still match the documents whose f is in the range.
 *
 * <p>The BinarySortField subclass needs its provider listed in
 * META-INF/services/org.apache.lucene.index.SortFieldProvider (as BinarySortField requires).
 */
public class TestPrimarySortFieldSkipsOrderingField extends LuceneTestCase {

  // (c, f) per document. Sorted by (c, f), the segment holds f = 5, 9, 1, 7, 3 in doc-ID order.
  private static final long[][] DOCS = {{1, 7}, {0, 9}, {2, 3}, {0, 5}, {1, 1}};

  private interface DocBuilder {
    void add(Document doc, long c, long f);
  }

  private static void index(Directory dir, Sort sort, DocBuilder builder) throws IOException {
    IndexWriterConfig iwc =
        new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()).setIndexSort(sort);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (long[] cf : DOCS) {
        Document doc = new Document();
        builder.add(doc, cf[0], cf[1]);
        w.addDocument(doc);
      }
      w.forceMerge(1);
    }
  }

  /** f:[1 TO 3] matches the two documents with f = 1 and f = 3. */
  private static void assertTwoHits(LeafReader reader, Query q) throws IOException {
    IndexSearcher searcher = new IndexSearcher(reader);
    searcher.setQueryCache(null);
    String msg = q + ", getPrimarySortField=" + Sort.getPrimarySortField(reader);
    assertEquals(msg, 2, searcher.search(q, 10).totalHits.value());
    assertEquals(msg, 2, searcher.count(q));
  }

  /** No wrapper: the first sort field is a BinarySortField whose key comes from field "a". */
  public void testBinarySortFieldWithKeyFromOtherField() throws IOException {
    Sort sort = new Sort(new KeyFromFieldA("c"), new SortField("f", SortField.Type.LONG));
    try (Directory dir = newDirectory()) {
      index(
          dir,
          sort,
          (doc, c, f) -> {
            doc.add(new NumericDocValuesField("a", c));
            doc.add(NumericDocValuesField.indexedField("f", f));
          });
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        LeafReader leaf = getOnlyLeafReader(reader);
        NumericDocValues f = leaf.getNumericDocValues("f");
        long[] order = new long[leaf.maxDoc()];
        for (int doc = 0; doc < order.length; doc++) {
          assertTrue(f.advanceExact(doc));
          order[doc] = f.longValue();
        }
        assertArrayEquals(new long[] {5, 9, 1, 7, 3}, order); // ordered by a, not by f
        assertTwoHits(leaf, NumericDocValuesField.newSlowRangeQuery("f", 1, 3));
      }
    }
  }

  /** A reader that hides the first sort field c: NumericDocValuesField with a skip index. */
  public void testHiddenFirstSortFieldNumericRange() throws IOException {
    Sort sort =
        new Sort(new SortField("c", SortField.Type.LONG), new SortField("f", SortField.Type.LONG));
    try (Directory dir = newDirectory()) {
      index(
          dir,
          sort,
          (doc, c, f) -> {
            doc.add(NumericDocValuesField.indexedField("c", c));
            doc.add(NumericDocValuesField.indexedField("f", f));
          });
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        LeafReader leaf = getOnlyLeafReader(reader);
        Query q = NumericDocValuesField.newSlowRangeQuery("f", 1, 3);
        assertTwoHits(leaf, q); // the plain reader is right
        LeafReader hidden = new HideField(leaf, "c");
        assertEquals(sort, hidden.getMetaData().sort()); // FilterLeafReader delegates getMetaData()
        assertTwoHits(hidden, q);
      }
    }
  }

  /** The same with LongField (points + doc values, no skip index needed). */
  public void testHiddenFirstSortFieldLongFieldRange() throws IOException {
    Sort sort =
        new Sort(
            new SortedNumericSortField("c", SortField.Type.LONG),
            new SortedNumericSortField("f", SortField.Type.LONG));
    try (Directory dir = newDirectory()) {
      index(
          dir,
          sort,
          (doc, c, f) -> {
            doc.add(new LongField("c", c, Field.Store.NO));
            doc.add(new LongField("f", f, Field.Store.NO));
          });
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        LeafReader leaf = getOnlyLeafReader(reader);
        Query q = LongField.newRangeQuery("f", 1, 3);
        assertTwoHits(leaf, q);
        assertTwoHits(new HideField(leaf, "c"), q);
      }
    }
  }

  /**
   * Hides one field everywhere (FieldInfos, terms, points, every doc-values type, the skipper and
   * norms) and keeps everything else, including getMetaData().
   */
  static final class HideField extends FilterLeafReader {
    private final String hidden;
    private final FieldInfos infos;

    HideField(LeafReader in, String hidden) {
      super(in);
      this.hidden = hidden;
      List<FieldInfo> keep = new ArrayList<>();
      for (FieldInfo fi : in.getFieldInfos()) {
        if (fi.name.equals(hidden) == false) {
          keep.add(fi);
        }
      }
      infos = new FieldInfos(keep.toArray(new FieldInfo[0]));
    }

    private boolean visible(String field) {
      return field.equals(hidden) == false;
    }

    @Override
    public FieldInfos getFieldInfos() {
      return infos;
    }

    @Override
    public Terms terms(String field) {
      return visible(field) ? super.terms(field) : null;
    }

    @Override
    public PointValues getPointValues(String field) {
      return visible(field) ? super.getPointValues(field) : null;
    }

    @Override
    public NumericDocValues getNumericDocValues(String field) throws IOException {
      return visible(field) ? super.getNumericDocValues(field) : null;
    }

    @Override
    public BinaryDocValues getBinaryDocValues(String field) throws IOException {
      return visible(field) ? super.getBinaryDocValues(field) : null;
    }

    @Override
    public SortedDocValues getSortedDocValues(String field) throws IOException {
      return visible(field) ? super.getSortedDocValues(field) : null;
    }

    @Override
    public SortedNumericDocValues getSortedNumericDocValues(String field) throws IOException {
      return visible(field) ? super.getSortedNumericDocValues(field) : null;
    }

    @Override
    public SortedSetDocValues getSortedSetDocValues(String field) throws IOException {
      return visible(field) ? super.getSortedSetDocValues(field) : null;
    }

    @Override
    public DocValuesSkipper getDocValuesSkipper(String field) {
      return visible(field) ? super.getDocValuesSkipper(field) : null;
    }

    @Override
    public NumericDocValues getNormValues(String field) throws IOException {
      return visible(field) ? super.getNormValues(field) : null;
    }

    @Override
    public CacheHelper getCoreCacheHelper() {
      return null;
    }

    @Override
    public CacheHelper getReaderCacheHelper() {
      return null;
    }
  }

  /**
   * A BinarySortField whose sort key is the sortable 8-byte encoding of the NUMERIC field "a", as
   * BinarySortField's javadoc allows ("derive an order-preserving key from the stored bytes and/or
   * other doc-values fields of the segment"). Its own field name is never indexed.
   */
  public static final class KeyFromFieldA extends BinarySortField {
    static final String NAME = "TestKeyFromFieldA";

    public KeyFromFieldA(String field) {
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

    /** Reads and writes {@link KeyFromFieldA} (registered through SPI). */
    public static final class Provider extends SortFieldProvider {
      public Provider() {
        super(NAME);
      }

      @Override
      public SortField readSortField(DataInput in) throws IOException {
        return new KeyFromFieldA(in.readString());
      }

      @Override
      public void writeSortField(SortField sf, DataOutput out) throws IOException {
        out.writeString(sf.getField());
      }
    }
  }
}
