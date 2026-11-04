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
import org.apache.lucene.document.column.Column;
import org.apache.lucene.document.column.ColumnBatch;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestAddBatchNegativeNumDocs extends LuceneTestCase {

  public void testNegativeNumDocs() throws Exception {
    ColumnBatch batch =
        new ColumnBatch() {
          @Override
          public int numDocs() {
            return -1;
          }

          @Override
          public Iterable<Column> columns() {
            return List.of();
          }
        };
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      w.addDocument(new Document());
      // expected: IllegalArgumentException, writer open, 1 doc committed; actual: -ea writer closed, -da 0 docs
      Throwable t = null;
      try {
        w.addBatch(batch);
      } catch (Throwable e) {
        t = e;
      }
      assertTrue("writer closed by " + w.getTragicException(), w.isOpen());
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(1, r.numDocs());
      }
      assertTrue("expected IllegalArgumentException, got " + t, t instanceof IllegalArgumentException);
    }
  }
}
