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
package org.apache.lucene.document.column;

import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

public class TestDictionaryColumnDocIdOutOfRange extends LuceneTestCase {

  public void testDocIdEqualToNumDocsDoesNotReachNextDocument() throws Exception {
    int[] docs = {0, 2};
    int[] ords = {0, 1};
    DictionaryColumn tag =
        new DictionaryColumn(
            "tag",
            SortedDocValuesField.TYPE,
            Column.Density.SPARSE,
            List.of(new BytesRef("a"), new BytesRef("b"))) {
          @Override
          public OrdinalsTupleCursor tuples() {
            return new OrdinalsTupleCursor() {
              int i = -1;

              @Override
              public int nextDoc() {
                return ++i < docs.length ? docs[i] : DocIdSetIterator.NO_MORE_DOCS;
              }

              @Override
              public int ordValue() {
                return ords[i];
              }
            };
          }
        };
    ColumnBatch batch =
        new ColumnBatch() {
          @Override
          public int numDocs() {
            return 2;
          }

          @Override
          public Iterable<Column> columns() {
            return List.of(tag);
          }
        };

    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w =
            new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
      try {
        w.addBatch(batch);
      } catch (IllegalArgumentException rejectingIsFine) {
      }
      w.addDocument(new Document());
      try (DirectoryReader r = DirectoryReader.open(w)) {
        LeafReader leaf = getOnlyLeafReader(r);
        SortedDocValues dv = leaf.getSortedDocValues("tag");
        int next = leaf.maxDoc() - 1;
        String value =
            dv != null && dv.advanceExact(next) ? dv.lookupOrd(dv.ordValue()).utf8ToString() : null;
        // expected: doc-id 2 >= numDocs never reaches the next document; actual: it reads "b"
        assertNull("tag of the document added after the batch", value);
      }
    }
  }
}
