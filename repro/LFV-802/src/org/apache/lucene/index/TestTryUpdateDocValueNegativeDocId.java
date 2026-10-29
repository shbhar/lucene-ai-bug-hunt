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
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestTryUpdateDocValueNegativeDocId extends LuceneTestCase {

  public void testNegativeDocIdOnLeafReader() throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (int i = 0; i < 4; i++) {
        Document doc = new Document();
        doc.add(new NumericDocValuesField("v", i));
        w.addDocument(doc);
      }
      long seqNo;
      try (DirectoryReader r = DirectoryReader.open(w)) {
        try {
          seqNo = w.tryUpdateDocValue(getOnlyLeafReader(r), -1, new NumericDocValuesField("v", 777));
        } catch (IllegalArgumentException rejected) {
          seqNo = -1; // rejecting the out-of-range docID is a correct outcome
        }
      }
      try (DirectoryReader r = DirectoryReader.open(w)) {
        NumericDocValues v = getOnlyLeafReader(r).getNumericDocValues("v");
        assertTrue(v.advanceExact(3));
        // Expected: docID -1 changes no document (doc 3 keeps 3); actual: seqNo > 0 and doc 3 = 777.
        assertEquals("tryUpdateDocValue(leaf, -1) returned " + seqNo + "; doc 3", 3, v.longValue());
      }
    }
  }
}
