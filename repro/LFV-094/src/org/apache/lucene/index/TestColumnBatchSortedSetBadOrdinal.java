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

import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.document.column.Column;
import org.apache.lucene.document.column.ColumnBatch;
import org.apache.lucene.document.column.DictionaryColumn;
import org.apache.lucene.document.column.OrdinalsTupleCursor;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

/**
 * addBatch correctly rejects a SORTED_SET DictionaryColumn ordinal that is out of range, but the
 * doc-values writer has already opened the document before the check, so the rejected document is
 * later recorded with zero values. Flushing the valid documents indexed afterwards then fails.
 */
public class TestColumnBatchSortedSetBadOrdinal extends LuceneTestCase {

  private static IndexWriterConfig config() {
    return new IndexWriterConfig()
        .setCodec(TestUtil.getDefaultCodec())
        .setMergePolicy(NoMergePolicy.INSTANCE);
  }

  public void testRejectedOrdinalDoesNotBreakLaterFlush() throws Exception {
    FieldType sortedSet = new FieldType();
    sortedSet.setDocValuesType(DocValuesType.SORTED_SET);
    sortedSet.freeze();
    List<BytesRef> dictionary = List.of(new BytesRef("x"), new BytesRef("y"));
    DictionaryColumn column =
        new DictionaryColumn("tags", sortedSet, Column.Density.SPARSE, dictionary) {
          @Override
          public OrdinalsTupleCursor tuples() {
            return new OrdinalsTupleCursor() {
              int pos = -1;

              @Override
              public int nextDoc() {
                pos++;
                return pos == 0 ? 0 : DocIdSetIterator.NO_MORE_DOCS;
              }

              @Override
              public int ordValue() {
                return 5; // out of range [0, 2)
              }
            };
          }
        };
    ColumnBatch batch =
        new ColumnBatch() {
          @Override
          public int numDocs() {
            return 1;
          }

          @Override
          public Iterable<Column> columns() {
            return List.of(column);
          }
        };

    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, config())) {
        IllegalArgumentException e =
            expectThrows(IllegalArgumentException.class, () -> w.addBatch(batch));
        assertTrue(e.getMessage(), e.getMessage().contains("out of range"));
        assertTrue(w.isOpen());

        Document doc = new Document();
        doc.add(new SortedSetDocValuesField("tags", new BytesRef("p")));
        doc.add(new SortedSetDocValuesField("tags", new BytesRef("q")));
        w.addDocument(doc);
        w.commit();
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(1, reader.numDocs());
        LeafReader leaf = reader.leaves().get(0).reader();
        SortedSetDocValues dv = leaf.getSortedSetDocValues("tags");
        int goodDoc = leaf.maxDoc() - 1;
        assertTrue(dv.advanceExact(goodDoc));
        assertEquals(2, dv.docValueCount());
        assertEquals(new BytesRef("p"), dv.lookupOrd(dv.nextOrd()));
        assertEquals(new BytesRef("q"), dv.lookupOrd(dv.nextOrd()));
      }
    }
  }
}
