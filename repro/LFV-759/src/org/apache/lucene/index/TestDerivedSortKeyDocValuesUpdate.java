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
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.lucene.document.BinaryDocValuesField;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.BinarySortField;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.DataInput;
import org.apache.lucene.store.DataOutput;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.BytesRefBuilder;
import org.apache.lucene.util.NumericUtils;

public class TestDerivedSortKeyDocValuesUpdate extends LuceneTestCase {

  public static final class PriceThenSkuSortField extends BinarySortField {
    public PriceThenSkuSortField() {
      super("sku", false, null, Provider.NAME);
    }

    @Override
    protected BinaryDocValues getSortKeyDocValues(LeafReader reader) throws IOException {
      NumericDocValues price = DocValues.getNumeric(reader, "price");
      BytesRefBuilder key = new BytesRefBuilder();
      return new FilterBinaryDocValues(DocValues.getBinary(reader, "sku")) {
        @Override
        public BytesRef binaryValue() throws IOException {
          while (price.docID() < docID()) {
            price.nextDoc();
          }
          key.grow(Long.BYTES);
          key.setLength(Long.BYTES);
          NumericUtils.longToSortableBytes(price.longValue(), key.bytes(), 0);
          key.append(in.binaryValue());
          return key.get();
        }
      };
    }

    public static final class Provider extends SortFieldProvider {
      static final String NAME = "PriceThenSku";

      public Provider() {
        super(NAME);
      }

      @Override
      public SortField readSortField(DataInput in) {
        return new PriceThenSkuSortField();
      }

      @Override
      public void writeSortField(SortField sf, DataOutput out) {}
    }
  }

  private static Document doc(String sku, long price) {
    Document doc = new Document();
    doc.add(new StringField("sku", sku, Field.Store.NO));
    doc.add(new BinaryDocValuesField("sku", new BytesRef(sku)));
    doc.add(new NumericDocValuesField("price", price));
    return doc;
  }

  public void testUpdateOfFieldReadBySortKey() throws Exception {
    Path spi = createTempDir();
    Path services = spi.resolve("META-INF/services/" + SortFieldProvider.class.getName());
    Files.createDirectories(services.getParent());
    Files.writeString(services, PriceThenSkuSortField.Provider.class.getName());
    try (URLClassLoader loader =
        new URLClassLoader(new URL[] {spi.toUri().toURL()}, getClass().getClassLoader())) {
      SortFieldProvider.reloadSortFieldProviders(loader);
    }

    try (Directory dir = new ByteBuffersDirectory()) {
      Sort indexSort = new Sort(new PriceThenSkuSortField());
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setIndexSort(indexSort))) {
        w.addDocument(doc("a", 5));
        w.addDocument(doc("c", 8));
        w.commit();
        try {
          w.updateNumericDocValue(new Term("sku", "c"), "price", 1);
        } catch (IllegalArgumentException rejected) {
        }
      }
      try (CheckIndex checker = new CheckIndex(dir)) {
        checker.setLevel(CheckIndex.Level.MIN_LEVEL_FOR_INTEGRITY_CHECKS);
        // expected: update rejected or segment kept sorted; actual: "docID=0 sorts after docID=1"
        assertNull(checker.checkIndex().segmentInfos.get(0).indexSortStatus.error);
      }
    }
  }
}
