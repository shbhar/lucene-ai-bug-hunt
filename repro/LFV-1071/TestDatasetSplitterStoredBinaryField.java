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
package org.apache.lucene.classification.utils;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestDatasetSplitterStoredBinaryField extends LuceneTestCase {

  public void testSplitAllFieldsWithStoredBinaryField() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    try (Directory dir = newDirectory();
        Directory train = newDirectory();
        Directory test = newDirectory();
        Directory cv = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        Document doc = new Document();
        doc.add(new SortedDocValuesField("cls", new BytesRef("a")));
        doc.add(new StoredField("payload", new byte[] {1, 2, 3}));
        w.addDocument(doc);
      }
      // expected: split (fieldNames omitted = all fields) copies the bytes; actual: IOException
      // "cannot set a BytesRef value on a tokenized field"
      new DatasetSplitter(0.5, 0)
          .split(DirectoryReader.open(dir), train, test, cv, analyzer, false, "cls");
      try (DirectoryReader r = DirectoryReader.open(test)) {
        assertEquals(
            new BytesRef(new byte[] {1, 2, 3}),
            r.storedFields().document(0).getBinaryValue("payload"));
      }
    }
  }
}
