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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestParallelLeafReaderAddIndexesStoredFields extends LuceneTestCase {

  private static void indexOneDoc(Directory dir, String field, String value) throws Exception {
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      doc.add(new StoredField(field, value));
      w.addDocument(doc);
    }
  }

  public void testAddIndexesOfParallelReader() throws Exception {
    Directory dirA = new ByteBuffersDirectory();
    Directory dirB = new ByteBuffersDirectory();
    Directory target = new ByteBuffersDirectory();
    indexOneDoc(dirA, "title", "Lucene in Action");
    indexOneDoc(dirB, "price", "42");
    try (DirectoryReader a = DirectoryReader.open(dirA);
        DirectoryReader b = DirectoryReader.open(dirB);
        ParallelLeafReader parallel =
            new ParallelLeafReader(false, getOnlyLeafReader(a), getOnlyLeafReader(b));
        IndexWriter w = new IndexWriter(target, new IndexWriterConfig())) {
      w.addIndexes(SlowCodecReaderWrapper.wrap(parallel));
    }
    try (DirectoryReader copy = DirectoryReader.open(target)) {
      Document doc = copy.storedFields().document(0);
      // expected price=42 in the copy; actual: the value is stored as a second "title" and price is null
      assertEquals("copied document: " + doc, "42", doc.get("price"));
    }
  }
}
