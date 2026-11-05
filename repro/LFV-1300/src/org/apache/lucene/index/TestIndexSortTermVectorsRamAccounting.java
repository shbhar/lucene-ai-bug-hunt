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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestIndexSortTermVectorsRamAccounting extends LuceneTestCase {

  public void testIndexSortCountsBufferedTermVectors() throws IOException {
    long unsorted = ramBytesUsed(false);
    long sorted = ramBytesUsed(true);
    // Expected: the index sort keeps buffered term vectors in ramBytesUsed(); actual: ~1/3 of it.
    assertTrue("sorted=" + sorted + " unsorted=" + unsorted, sorted >= unsorted * 0.9);
  }

  private static long ramBytesUsed(boolean indexSort) throws IOException {
    FieldType ft = new FieldType(TextField.TYPE_NOT_STORED);
    ft.setStoreTermVectors(true);
    ft.setStoreTermVectorPositions(true);
    ft.setStoreTermVectorOffsets(true);
    IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
    if (indexSort) {
      iwc.setIndexSort(new Sort(new SortField("sort", SortField.Type.LONG)));
    }
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < 10; i++) {
        Document doc = new Document();
        doc.add(new NumericDocValuesField("sort", i));
        doc.add(new Field("body", "a ".repeat(10_000), ft));
        w.addDocument(doc);
      }
      return w.ramBytesUsed();
    }
  }
}
