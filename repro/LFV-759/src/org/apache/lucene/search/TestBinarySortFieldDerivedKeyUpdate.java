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
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.lucene.document.BinaryDocValuesField;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocValues;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.SortFieldProvider;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.DataInput;
import org.apache.lucene.store.DataOutput;
import org.apache.lucene.tests.store.BaseDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.BytesRefBuilder;
import org.apache.lucene.util.NumericUtils;
import org.junit.BeforeClass;

/**
 * A BinarySortField subclass whose sort key is derived from another doc-values field of the
 * segment, as the BinarySortField javadoc allows. IndexWriter rejects doc-values updates of the
 * sort's own field ("sku") but accepts updates of the field the key is derived from ("price"), and
 * afterwards the segment is no longer in its declared index-sort order.
 */
public class TestBinarySortFieldDerivedKeyUpdate extends LuceneTestCase {

  /**
   * Sorts by the numeric doc-values field "price" (encoded as 8 sortable bytes), ties broken by the
   * bytes of the sort's own binary field. Registered via
   * META-INF/services/org.apache.lucene.index.SortFieldProvider.
   */
  public static final class PriceThenSkuSortField extends BinarySortField {
    static final String NAME = "PriceThenSkuSortField";

    public PriceThenSkuSortField(String field, boolean reverse) {
      super(field, reverse, null, NAME);
    }

    @Override
    protected BinaryDocValues getSortKeyDocValues(LeafReader reader) throws IOException {
      final NumericDocValues price = DocValues.getNumeric(reader, "price");
      final BinaryDocValues tieBreak = DocValues.getBinary(reader, getField());
      return new BinaryDocValues() {
        private final BytesRefBuilder key = new BytesRefBuilder();
        private final byte[] sortable = new byte[Long.BYTES];
        private int keyDoc = -1;

        @Override
        public BytesRef binaryValue() throws IOException {
          int doc = price.docID();
          if (keyDoc != doc) {
            NumericUtils.longToSortableBytes(price.longValue(), sortable, 0);
            key.copyBytes(sortable, 0, sortable.length);
            int t = tieBreak.docID();
            while (t < doc) {
              t = tieBreak.nextDoc();
            }
            if (t == doc) {
              key.append(tieBreak.binaryValue());
            }
            keyDoc = doc;
          }
          return key.get();
        }

        @Override
        public int docID() {
          return price.docID();
        }

        @Override
        public int nextDoc() throws IOException {
          return price.nextDoc();
        }

        @Override
        public int advance(int target) throws IOException {
          return price.advance(target);
        }

        @Override
        public boolean advanceExact(int target) throws IOException {
          return target == keyDoc || price.advanceExact(target);
        }

        @Override
        public long cost() {
          return price.cost();
        }
      };
    }

    /** Reads back {@link PriceThenSkuSortField} from the segment info. */
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

  /**
   * The provider is normally registered through
   * META-INF/services/org.apache.lucene.index.SortFieldProvider on the test classpath (a copy is
   * next to this source). If that file is not on the classpath, register it here.
   */
  @BeforeClass
  public static void registerSortFieldProvider() throws Exception {
    if (SortFieldProvider.availableSortFieldProviders().contains(PriceThenSkuSortField.NAME)) {
      return;
    }
    Path dir = createTempDir("spi");
    Path services = dir.resolve("META-INF/services/" + SortFieldProvider.class.getName());
    Files.createDirectories(services.getParent());
    Files.writeString(
        services, PriceThenSkuSortField.Provider.class.getName() + "\n", StandardCharsets.UTF_8);
    try (URLClassLoader loader =
        new URLClassLoader(
            new URL[] {dir.toUri().toURL()},
            TestBinarySortFieldDerivedKeyUpdate.class.getClassLoader())) {
      SortFieldProvider.reloadSortFieldProviders(loader);
    }
  }

  private static Document doc(String id, long price) {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.YES));
    doc.add(new NumericDocValuesField("price", price));
    doc.add(new BinaryDocValuesField("sku", new BytesRef(id)));
    return doc;
  }

  private static String topId(IndexSearcher searcher, Sort sort) throws IOException {
    TopFieldDocs top = searcher.search(MatchAllDocsQuery.INSTANCE, 1, sort);
    return searcher.storedFields().document(top.scoreDocs[0].doc).get("id");
  }

  private static final Sort INDEX_SORT = new Sort(new PriceThenSkuSortField("sku", false));

  /**
   * Indexes a(5), b(7), c(8) into one segment sorted by price, then updates c's price to 1. Returns
   * false if the writer rejected that update (which would keep the segment sorted).
   */
  private static boolean indexAndUpdate(BaseDirectoryWrapper dir) throws IOException {
    IndexWriterConfig iwc =
        new IndexWriterConfig().setIndexSort(INDEX_SORT).setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      w.addDocument(doc("a", 5));
      w.addDocument(doc("b", 7));
      w.addDocument(doc("c", 8));
      w.commit();

      // the field the sort key is derived from (the sort's own field "sku" is protected, see
      // testUpdateOfSortFieldIsRejected)
      try {
        w.updateNumericDocValue(new Term("id", "c"), "price", 1);
      } catch (IllegalArgumentException rejected) {
        return false;
      }
      w.commit();
      return true;
    }
  }

  /** Control: the guard exists for the sort's own field (passes on main). */
  public void testUpdateOfSortFieldIsRejected() throws Exception {
    try (BaseDirectoryWrapper dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setIndexSort(INDEX_SORT);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        w.addDocument(doc("a", 5));
        w.commit();
        expectThrows(
            IllegalArgumentException.class,
            () -> w.updateBinaryDocValue(new Term("id", "a"), "sku", new BytesRef("z")));
      }
    }
  }

  public void testSearchSortedByIndexSortReturnsWrongTopHit() throws Exception {
    try (BaseDirectoryWrapper dir = newDirectory()) {
      dir.setCheckIndexOnClose(false); // see testCheckIndex
      if (indexAndUpdate(dir) == false) {
        return;
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        // same order without the index-sort shortcut (search sort is not a prefix of the index sort)
        assertEquals(
            "c", topId(searcher, new Sort(new PriceThenSkuSortField("sku", false), SortField.FIELD_DOC)));
        // sorted by the index sort itself: the collector trusts the segment order
        assertEquals("top hit sorted by the index sort", "c", topId(searcher, INDEX_SORT));
      }
    }
  }

  public void testCheckIndex() throws Exception {
    try (BaseDirectoryWrapper dir = newDirectory()) {
      dir.setCheckIndexOnClose(false); // checked explicitly
      if (indexAndUpdate(dir) == false) {
        return;
      }
      // fails: segment has indexSort=<binary: "sku"> but docID=1 sorts after docID=2
      TestUtil.checkIndex(dir);
    }
  }
}
