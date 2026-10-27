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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import org.apache.lucene.document.BinaryDocValuesField;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.CheckIndex;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocValues;
import org.apache.lucene.index.FilterBinaryDocValues;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LogDocMergePolicy;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SortFieldProvider;
import org.apache.lucene.index.Term;
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
 * A {@link BinarySortField} subclass may derive its index-sort key from "other doc-values fields of
 * the segment". IndexWriter rejects in-place updates of the sort's own field, but not of the field
 * the key is read from, and the segment then no longer follows the sort it declares.
 *
 * <p>Needs one line in
 * lucene/core/src/test/META-INF/services/org.apache.lucene.index.SortFieldProvider: {@code
 * org.apache.lucene.search.TestBinarySortFieldCompanionUpdate$PriceThenSkuSortField$Provider}
 */
public class TestBinarySortFieldCompanionUpdate extends LuceneTestCase {

  /** Sort key = the numeric field "price" (8 sortable bytes), ties broken by the bytes of "sku". */
  public static final class PriceThenSkuSortField extends BinarySortField {
    static final String NAME = "PriceThenSkuSortField";

    public PriceThenSkuSortField(String field, boolean reverse) {
      super(field, reverse, null, NAME);
    }

    @Override
    protected BinaryDocValues getSortKeyDocValues(LeafReader reader) throws IOException {
      NumericDocValues price = DocValues.getNumeric(reader, "price");
      BytesRefBuilder key = new BytesRefBuilder();
      byte[] priceBytes = new byte[Long.BYTES];
      return new FilterBinaryDocValues(DocValues.getBinary(reader, getField())) {
        @Override
        public BytesRef binaryValue() throws IOException {
          int doc = docID();
          while (price.docID() < doc) {
            price.nextDoc(); // nextDoc() only, as getSortKeyDocValues asks
          }
          long p = price.docID() == doc ? price.longValue() : Long.MAX_VALUE;
          NumericUtils.longToSortableBytes(p, priceBytes, 0);
          key.copyBytes(priceBytes, 0, priceBytes.length);
          key.append(in.binaryValue());
          return key.get();
        }
      };
    }

    /** Reads the sort back from the segment info, as the BinarySortField javadoc requires. */
    public static final class Provider extends SortFieldProvider {
      public Provider() {
        super(NAME);
      }

      @Override
      public SortField readSortField(DataInput in) throws IOException {
        return new PriceThenSkuSortField(in.readString(), in.readInt() == 1);
      }

      @Override
      public void writeSortField(SortField sf, DataOutput out) throws IOException {
        out.writeString(sf.getField());
        out.writeInt(sf.getReverse() ? 1 : 0);
      }
    }
  }

  private static final Sort INDEX_SORT = new Sort(new PriceThenSkuSortField("sku", false));

  private static IndexWriterConfig config() {
    return new IndexWriterConfig()
        .setCodec(TestUtil.getDefaultCodec())
        .setIndexSort(INDEX_SORT)
        .setMergePolicy(NoMergePolicy.INSTANCE);
  }

  private static Document doc(String id, long price) {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.YES));
    doc.add(new NumericDocValuesField("price", price));
    doc.add(new BinaryDocValuesField("sku", new BytesRef(id)));
    return doc;
  }

  /**
   * One segment, stored sorted by price as a(5), b(7), c(8); then c's price is set to 1. Returns
   * false if IndexWriter rejected that update (then the segment stays sorted).
   */
  private static boolean indexAndUpdatePrice(Directory dir) throws IOException {
    try (IndexWriter w = new IndexWriter(dir, config())) {
      w.addDocument(doc("c", 8));
      w.addDocument(doc("a", 5));
      w.addDocument(doc("b", 7));
      w.commit();
      try {
        w.updateNumericDocValue(new Term("id", "c"), "price", 1);
      } catch (IllegalArgumentException _) {
        return false;
      }
      w.commit();
      return true;
    }
  }

  private static String storedOrder(DirectoryReader reader) throws IOException {
    assertEquals(1, reader.leaves().size());
    StringBuilder order = new StringBuilder();
    for (int doc = 0; doc < reader.maxDoc(); doc++) {
      order.append(reader.storedFields().document(doc).get("id"));
    }
    return order.toString();
  }

  private static String topId(IndexSearcher searcher, Sort sort) throws IOException {
    TopFieldDocs top = searcher.search(MatchAllDocsQuery.INSTANCE, 1, sort);
    return searcher.storedFields().document(top.scoreDocs[0].doc).get("id");
  }

  /** Control: an update of the sort's own field is rejected. */
  public void testUpdateOfSortFieldIsRejected() throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, config())) {
      w.addDocument(doc("a", 5));
      w.commit();
      expectThrows(
          IllegalArgumentException.class,
          () -> w.updateBinaryDocValue(new Term("id", "a"), "sku", new BytesRef("z")));
    }
  }

  public void testCheckIndexAfterPriceUpdate() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      if (indexAndUpdatePrice(dir) == false) {
        return;
      }
      ByteArrayOutputStream log = new ByteArrayOutputStream();
      CheckIndex.Status status;
      try (CheckIndex checker = new CheckIndex(dir)) {
        checker.setLevel(CheckIndex.Level.MIN_LEVEL_FOR_INTEGRITY_CHECKS); // the sort check
        checker.setInfoStream(new PrintStream(log, true, UTF_8));
        status = checker.checkIndex();
      }
      assertTrue(log.toString(UTF_8), status.clean);
    }
  }

  public void testTopHitSortedByIndexSortAfterPriceUpdate() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      if (indexAndUpdatePrice(dir) == false) {
        return;
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        // not a prefix of the index sort, so it compares every document: c, whose price is now 1
        Sort fullScan = new Sort(new PriceThenSkuSortField("sku", false), SortField.FIELD_DOC);
        assertEquals("c", topId(searcher, fullScan));
        // the index sort itself: the collector trusts the segment order and stops early
        assertEquals("top hit sorted by the index sort", "c", topId(searcher, INDEX_SORT));
      }
    }
  }

  public void testMergeAfterPriceUpdate() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      if (indexAndUpdatePrice(dir) == false) {
        return;
      }
      try (IndexWriter w = new IndexWriter(dir, config().setMergePolicy(new LogDocMergePolicy()))) {
        w.addDocument(doc("d", 6));
        w.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        // prices c=1, a=5, d=6, b=7
        assertEquals("stored order of the merged segment", "cadb", storedOrder(reader));
      }
    }
  }
}
