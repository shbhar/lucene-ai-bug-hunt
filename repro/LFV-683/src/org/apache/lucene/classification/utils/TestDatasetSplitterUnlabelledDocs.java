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

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestDatasetSplitterUnlabelledDocs extends LuceneTestCase {

  private static Document doc(String id, String cls) {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.YES));
    if (cls != null) {
      doc.add(new SortedDocValuesField("cls", new BytesRef(cls)));
    }
    return doc;
  }

  public void testUnlabelledDocumentDropsLabelledClass() throws Exception {
    Directory src = new ByteBuffersDirectory();
    try (IndexWriter w = new IndexWriter(src, new IndexWriterConfig())) {
      w.addDocument(doc("d0", null));
      w.addDocument(doc("d1", "a"));
      w.addDocument(doc("d2", "b"));
    }
    Directory[] outputs = {
      new ByteBuffersDirectory(), new ByteBuffersDirectory(), new ByteBuffersDirectory()
    };
    new DatasetSplitter(0.1, 0.1)
        .split(
            DirectoryReader.open(src),
            outputs[0],
            outputs[1],
            outputs[2],
            new StandardAnalyzer(),
            false,
            "cls");
    List<String> ids = new ArrayList<>();
    for (Directory out : outputs) {
      try (DirectoryReader r = DirectoryReader.open(out)) {
        for (int i = 0; i < r.maxDoc(); i++) {
          ids.add(r.storedFields().document(i).get("id"));
        }
      }
    }
    assertTrue(
        "expected d2 (the only document of class b) in one of the outputs, but they hold " + ids,
        ids.contains("d2"));
  }
}
