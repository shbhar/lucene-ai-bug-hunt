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
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.BinaryDocValuesField;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.BinarySortField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.DataInput;
import org.apache.lucene.store.DataOutput;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.BytesRefBuilder;
import org.apache.lucene.util.NumericUtils;

/**
 * BinarySortField's class javadoc allows a subclass to derive its index-sort key from "other
 * doc-values fields of the segment". IndexWriter rejects doc-values updates of fields "involved in
 * the index sort", but only by SortField#getField name. This test indexes with a subclass whose key
 * is (price, sku), declared on field "sku", then updates the doc-values-only field "price" in place
 * and checks that the index still follows its declared sort (or that the update was rejected).
 *
 * <p>Public API only. The SortFieldProvider is registered through
 * META-INF/services/org.apache.lucene.index.SortFieldProvider (put the resource dir on the
 * classpath).
 */
public class TestDerivedSortKeyCompanionUpdate extends LuceneTestCase {

  /** Key = sortable 8-byte price, then the sku bytes. Declared on the "sku" field. */
  public static final class PriceSkuSortField extends BinarySortField {
    static final String NAME = "PriceSkuSortField";
    static final String PRICE = "price";

    public PriceSkuSortField(String field, boolean reverse) {
      super(field, reverse, null, NAME);
    }

    @Override
    protected BinaryDocValues getSortKeyDocValues(LeafReader reader) throws IOException {
      final BinaryDocValues sku = DocValues.getBinary(reader, getField());
      final NumericDocValues price = DocValues.getNumeric(reader, PRICE);
      final BytesRefBuilder key = new BytesRefBuilder();
      final byte[] priceBytes = new byte[Long.BYTES];
      return new BinaryDocValues() {
        @Override
        public BytesRef binaryValue() throws IOException {
          int doc = sku.docID();
          // only nextDoc() on the companion field, as the javadoc requires
          while (price.docID() < doc) {
            price.nextDoc();
          }
          long p = price.docID() == doc ? price.longValue() : Long.MAX_VALUE;
          NumericUtils.longToSortableBytes(p, priceBytes, 0);
          key.clear();
          key.append(priceBytes, 0, priceBytes.length);
          key.append(sku.binaryValue());
          return key.get();
        }

        @Override
        public int docID() {
          return sku.docID();
        }

        @Override
        public int nextDoc() throws IOException {
          return sku.nextDoc();
        }

        @Override
        public int advance(int target) throws IOException {
          return sku.advance(target);
        }

        @Override
        public boolean advanceExact(int target) throws IOException {
          return sku.advanceExact(target);
        }

        @Override
        public long cost() {
          return sku.cost();
        }
      };
    }

    /** Test helper: the sort keys of a leaf, in docID order. */
    List<BytesRef> keys(LeafReader reader) throws IOException {
      List<BytesRef> out = new ArrayList<>();
      BinaryDocValues dv = getSortKeyDocValues(reader);
      for (int d = dv.nextDoc(); d != BinaryDocValues.NO_MORE_DOCS; d = dv.nextDoc()) {
        out.add(BytesRef.deepCopyOf(dv.binaryValue()));
      }
      return out;
    }

    /** SPI provider that reconstructs the subclass when the segment's sort is read back. */
    public static final class Provider extends SortFieldProvider {
      public Provider() {
        super(NAME);
      }

      @Override
      public SortField readSortField(DataInput in) throws IOException {
        return new PriceSkuSortField(in.readString(), in.readInt() == 1);
      }

      @Override
      public void writeSortField(SortField sf, DataOutput out) throws IOException {
        out.writeString(sf.getField());
        out.writeInt(sf.getReverse() ? 1 : 0);
      }
    }
  }

  private static Document doc(String id, long price) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    d.add(new NumericDocValuesField(PriceSkuSortField.PRICE, price));
    d.add(new BinaryDocValuesField("sku", new BytesRef(id)));
    return d;
  }

  private static IndexWriterConfig config(SortField sf) {
    IndexWriterConfig iwc = new IndexWriterConfig();
    iwc.setCodec(TestUtil.getDefaultCodec());
    iwc.setIndexSort(new Sort(sf));
    iwc.setMergePolicy(NoMergePolicy.INSTANCE);
    return iwc;
  }

  /**
   * Builds one flushed segment with a(5), b(7), c(8) (added out of order, so the flush sort runs),
   * then updates c's price to 1. Returns true if the update was accepted.
   */
  private static boolean buildAndUpdate(Directory dir, PriceSkuSortField sf) throws IOException {
    try (IndexWriter w = new IndexWriter(dir, config(sf))) {
      w.addDocument(doc("c", 8));
      w.addDocument(doc("a", 5));
      w.addDocument(doc("b", 7));
      w.commit();
      try {
        w.updateNumericDocValue(new Term("id", "c"), PriceSkuSortField.PRICE, 1);
      } catch (IllegalArgumentException expectedIfFixed) {
        return false;
      }
      w.commit();
      return true;
    }
  }

  private static String id(IndexSearcher s, int doc) throws IOException {
    return s.storedFields().document(doc).get("id");
  }

  /** Control: an update of the declared sort field itself is rejected (main has #16565). */
  public void testUpdateOfDeclaredSortFieldIsRejected() throws IOException {
    PriceSkuSortField sf = new PriceSkuSortField("sku", false);
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, config(sf))) {
      w.addDocument(doc("a", 5));
      w.commit();
      expectThrows(
          IllegalArgumentException.class,
          () -> w.updateBinaryDocValue(new Term("id", "a"), "sku", new BytesRef("z")));
    }
  }

  /** Sanity: the derived key really drives the flush sort (c(8) is added first, stored last). */
  public void testFlushSortsByDerivedKey() throws IOException {
    PriceSkuSortField sf = new PriceSkuSortField("sku", false);
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, config(sf))) {
        w.addDocument(doc("c", 8));
        w.addDocument(doc("a", 5));
        w.addDocument(doc("b", 7));
        w.commit();
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(1, r.leaves().size());
        IndexSearcher s = new IndexSearcher(r);
        assertEquals("a", id(s, 0));
        assertEquals("b", id(s, 1));
        assertEquals("c", id(s, 2));
      }
      TestUtil.checkIndex(dir);
    }
  }

  /** Every segment that declares the index sort must store its docs in that order. */
  public void testSegmentFollowsDeclaredSortAfterCompanionUpdate() throws IOException {
    PriceSkuSortField sf = new PriceSkuSortField("sku", false);
    try (Directory dir = new ByteBuffersDirectory()) {
      if (buildAndUpdate(dir, sf) == false) {
        return; // rejected like an update of the sort field: the index sort is protected
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        for (LeafReaderContext ctx : r.leaves()) {
          LeafReader leaf = ctx.reader();
          assertEquals(new Sort(sf), leaf.getMetaData().sort());
          List<BytesRef> keys = sf.keys(leaf);
          for (int i = 1; i < keys.size(); i++) {
            assertTrue(
                "segment declares sort "
                    + sf
                    + " but docID="
                    + (i - 1)
                    + " has a larger key than docID="
                    + i,
                keys.get(i - 1).compareTo(keys.get(i)) <= 0);
          }
        }
      }
    }
  }

  /** CheckIndex must find the index clean after a legal update. */
  public void testCheckIndexCleanAfterCompanionUpdate() throws IOException {
    PriceSkuSortField sf = new PriceSkuSortField("sku", false);
    try (Directory dir = new ByteBuffersDirectory()) {
      if (buildAndUpdate(dir, sf) == false) {
        return;
      }
      TestUtil.checkIndex(dir);
    }
  }

  /**
   * A top-1 search sorted by the index sort (eligible for early termination) must return the same
   * hit as the same sort with a FIELD_DOC tie-break (not a prefix of the index sort, so no early
   * termination): c, whose price is now 1.
   */
  public void testTopHitSortedByIndexSortAfterCompanionUpdate() throws IOException {
    PriceSkuSortField sf = new PriceSkuSortField("sku", false);
    try (Directory dir = new ByteBuffersDirectory()) {
      if (buildAndUpdate(dir, sf) == false) {
        return;
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        TopDocs full = s.search(MatchAllDocsQuery.INSTANCE, 1, new Sort(sf, SortField.FIELD_DOC));
        TopDocs early = s.search(MatchAllDocsQuery.INSTANCE, 1, new Sort(sf));
        assertEquals("reference (no early termination)", "c", id(s, full.scoreDocs[0].doc));
        assertEquals("sorted by the index sort", "c", id(s, early.scoreDocs[0].doc));
      }
    }
  }

  /** Same check through the generic updateDocValues(Term, Field...) path. */
  public void testCheckIndexCleanAfterCompanionUpdateViaUpdateDocValues() throws IOException {
    PriceSkuSortField sf = new PriceSkuSortField("sku", false);
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, config(sf))) {
        w.addDocument(doc("c", 8));
        w.addDocument(doc("a", 5));
        w.addDocument(doc("b", 7));
        w.commit();
        try {
          w.updateDocValues(
              new Term("id", "c"), new NumericDocValuesField(PriceSkuSortField.PRICE, 1));
        } catch (IllegalArgumentException expectedIfFixed) {
          return;
        }
        w.commit();
      }
      TestUtil.checkIndex(dir);
    }
  }

  /** The disorder is carried into merged segments: a merge assumes sorted inputs. */
  public void testMergedSegmentFollowsDeclaredSortAfterCompanionUpdate() throws IOException {
    PriceSkuSortField sf = new PriceSkuSortField("sku", false);
    try (Directory dir = new ByteBuffersDirectory()) {
      if (buildAndUpdate(dir, sf) == false) {
        return;
      }
      IndexWriterConfig iwc = config(sf).setMergePolicy(newLogMergePolicy());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        w.addDocument(doc("d", 6));
        w.forceMerge(1);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(1, r.leaves().size());
        LeafReader leaf = r.leaves().get(0).reader();
        assertEquals(new Sort(sf), leaf.getMetaData().sort());
        IndexSearcher s = new IndexSearcher(r);
        StringBuilder order = new StringBuilder();
        for (int d = 0; d < leaf.maxDoc(); d++) {
          order.append(id(s, d));
        }
        // keys: a=5, b=7, c=1 (updated), d=6 -> declared order is c, a, d, b
        assertEquals("stored order of the merged segment", "cadb", order.toString());
      }
    }
  }
}
