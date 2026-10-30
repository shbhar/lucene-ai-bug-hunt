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
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestIndexSortRejectedLastDocument extends LuceneTestCase {

  public void testCommitAfterRejectedDocument() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig();
      iwc.setIndexSort(new Sort(new SortField("sort", SortField.Type.LONG)));
      iwc.setParentField("parent");
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        w.addDocuments(List.of(List.of(), List.of(new NumericDocValuesField("f", 1))));
        expectThrows(
            IllegalArgumentException.class,
            () -> w.addDocument(List.of(new SortedDocValuesField("f", new BytesRef("x")))));
        // expected: commit keeps the 2 block docs; actual: ArrayIndexOutOfBoundsException 2147483647, writer closed
        w.commit();
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(2, r.numDocs());
      }
    }
  }
}
