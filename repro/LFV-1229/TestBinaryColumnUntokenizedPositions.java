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
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestBinaryColumnUntokenizedPositions extends LuceneTestCase {

  public void testUntokenizedColumnWithPositions() throws Exception {
    // Expected: indexed like addDocument(new Field("f", new BytesRef("x"), type)); actual: IAE
    FieldType type = new FieldType();
    type.setTokenized(false);
    type.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
    BinaryColumn column =
        new BinaryColumn("f", type, Column.Density.SPARSE) {
          @Override
          public ObjectTupleCursor<BytesRef> tuples() {
            return new ObjectTupleCursor<>() {
              int doc = -1;

              @Override
              public int nextDoc() {
                return doc = doc == -1 ? 0 : DocIdSetIterator.NO_MORE_DOCS;
              }

              @Override
              public BytesRef value() {
                return new BytesRef("x");
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
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      w.addBatch(batch);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        PostingsEnum pe = getOnlyLeafReader(r).postings(new Term("f", "x"), PostingsEnum.POSITIONS);
        assertEquals(0, pe.nextDoc());
        assertEquals(0, pe.nextPosition());
      }
    }
  }
}
