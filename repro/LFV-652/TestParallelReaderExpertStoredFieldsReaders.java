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
import org.apache.lucene.document.StoredField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Wider trigger of the same defect through the expert constructor: a storedFieldsReader that is not
 * one of the main readers contributes stored fields that the parallel reader's FieldInfos do not
 * list. Copying it renames the field (empty target) or fails with a NullPointerException (target
 * whose field numbers differ).
 */
public class TestParallelReaderExpertStoredFieldsReaders extends LuceneTestCase {

  private static void addDoc(Directory dir, String... fields) throws IOException {
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      for (String f : fields) {
        doc.add(new StoredField(f, f + "-value"));
      }
      w.addDocument(doc);
    }
  }

  public void testStoredOnlyReaderCopiedIntoEmptyIndex() throws Exception {
    try (Directory main = newDirectory();
        Directory stored = newDirectory();
        Directory target = newDirectory()) {
      addDoc(main, "a", "b");
      addDoc(stored, "x");
      try (DirectoryReader rm = DirectoryReader.open(main);
          DirectoryReader rs = DirectoryReader.open(stored)) {
        LeafReader m = getOnlyLeafReader(rm);
        ParallelLeafReader parallel =
            new ParallelLeafReader(
                false, new LeafReader[] {m}, new LeafReader[] {m, getOnlyLeafReader(rs)});
        assertEquals("x-value", parallel.storedFields().document(0).get("x"));
        try (IndexWriter w = new IndexWriter(target, new IndexWriterConfig())) {
          w.addIndexes(SlowCodecReaderWrapper.wrap(parallel));
        }
        parallel.close();
      }
      try (DirectoryReader copy = DirectoryReader.open(target)) {
        Document doc = copy.storedFields().document(0);
        assertEquals("stored fields of the copy: " + doc, "x-value", doc.get("x"));
        assertEquals("stored fields of the copy: " + doc, 1, doc.getValues("a").length);
      }
    }
  }
}
