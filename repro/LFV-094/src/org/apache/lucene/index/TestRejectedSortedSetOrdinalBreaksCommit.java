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

public class TestRejectedSortedSetOrdinalBreaksCommit extends LuceneTestCase {

  public void testCommitAfterRejectedOrdinal() throws Exception {
    DictionaryColumn column =
        new DictionaryColumn(
            "tags",
            SortedSetDocValuesField.TYPE,
            Column.Density.SPARSE,
            List.of(new BytesRef("x"))) {
          @Override
          public OrdinalsTupleCursor tuples() {
            return new OrdinalsTupleCursor() {
              int doc = -1;

              @Override
              public int nextDoc() {
                doc = doc == -1 ? 0 : DocIdSetIterator.NO_MORE_DOCS;
                return doc;
              }

              @Override
              public int ordValue() {
                return 1;
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
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w =
            new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
      // ordinal 1 is out of range: expected a rejection that leaves no trace; actual: commit fails
      expectThrows(IllegalArgumentException.class, () -> w.addBatch(batch));
      Document doc = new Document();
      doc.add(new SortedSetDocValuesField("tags", new BytesRef("p")));
      doc.add(new SortedSetDocValuesField("tags", new BytesRef("q")));
      w.addDocument(doc);
      try {
        w.commit();
      } catch (Throwable t) {
        throw new AssertionError("expected commit to succeed after the rejection, got " + t, t);
      }
    }
  }
}
