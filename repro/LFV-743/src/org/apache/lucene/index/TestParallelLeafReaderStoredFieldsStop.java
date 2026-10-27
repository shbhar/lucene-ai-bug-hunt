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
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * StoredFieldVisitor.Status.STOP: "don't visit this field and stop processing any other fields for
 * this document". ParallelLeafReader#storedFields() hands the visitor to every parallel reader in
 * turn, so a STOP returned while the first reader is visited does not stop the second one.
 */
public class TestParallelLeafReaderStoredFieldsStop extends LuceneTestCase {

  /** Records every callback; stops the document at the field named "title". */
  private static final class StopAtTitleVisitor extends StoredFieldVisitor {
    final List<String> calls = new ArrayList<>();

    @Override
    public Status needsField(FieldInfo fieldInfo) {
      calls.add("needsField(" + fieldInfo.name + ")");
      return fieldInfo.name.equals("title") ? Status.STOP : Status.YES;
    }

    @Override
    public void stringField(FieldInfo fieldInfo, String value) {
      calls.add("stringField(" + fieldInfo.name + "=" + value + ")");
    }
  }

  private static void addOneDoc(Directory dir, String... nameValues) throws IOException {
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      for (int i = 0; i < nameValues.length; i += 2) {
        doc.add(new StoredField(nameValues[i], nameValues[i + 1]));
      }
      w.addDocument(doc);
    }
  }

  public void testStopEndsTheDocumentAcrossParallelReaders() throws Exception {
    try (Directory single = newDirectory();
        Directory dirA = newDirectory();
        Directory dirB = newDirectory()) {
      // Control: one index with both fields. STOP at "title" ends the document.
      addOneDoc(single, "title", "Lucene in Action", "price", "42");
      try (DirectoryReader r = DirectoryReader.open(single)) {
        StopAtTitleVisitor v = new StopAtTitleVisitor();
        r.storedFields().document(0, v);
        assertEquals(List.of("needsField(title)"), v.calls);
      }

      // The same document split over two parallel indexes, as the ParallelLeafReader javadoc asks.
      addOneDoc(dirA, "title", "Lucene in Action");
      addOneDoc(dirB, "price", "42");
      try (DirectoryReader a = DirectoryReader.open(dirA);
          DirectoryReader b = DirectoryReader.open(dirB);
          ParallelLeafReader pr =
              new ParallelLeafReader(false, getOnlyLeafReader(a), getOnlyLeafReader(b))) {
        // the parallel reader exposes the union of the fields
        Document all = pr.storedFields().document(0);
        assertEquals("Lucene in Action", all.get("title"));
        assertEquals("42", all.get("price"));

        StopAtTitleVisitor v = new StopAtTitleVisitor();
        pr.storedFields().document(0, v);
        assertEquals(
            "no callback may follow STOP for this document", List.of("needsField(title)"), v.calls);
      }
    }
  }
}
