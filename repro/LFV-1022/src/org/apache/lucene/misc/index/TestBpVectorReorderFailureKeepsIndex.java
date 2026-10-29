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
package org.apache.lucene.misc.index;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestBpVectorReorderFailureKeepsIndex extends LuceneTestCase {

  // Expected: a reorder that throws leaves the index's 1 document; actual: the index is committed empty.
  public void testFailedReorderKeepsIndex() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        doc.add(new KnnFloatVectorField("f", new float[] {1, 2}));
        w.addDocument(doc);
      }
      BpVectorReorderer reorderer = new BpVectorReorderer("f");
      reorderer.setRAMBudgetMB(Double.MIN_VALUE);
      expectThrows(
          AbstractBPReorderer.NotEnoughRAMException.class,
          () -> reorderer.reorderIndexDirectory(dir, null));
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(1, reader.numDocs());
      }
    }
  }
}
