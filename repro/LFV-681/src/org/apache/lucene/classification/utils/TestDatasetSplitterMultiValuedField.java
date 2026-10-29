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

import java.util.Arrays;
import java.util.List;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestDatasetSplitterMultiValuedField extends LuceneTestCase {

  public void testNamedFieldKeepsAllValues() throws Exception {
    try (Directory src = new ByteBuffersDirectory();
        Directory train = new ByteBuffersDirectory();
        Directory test = new ByteBuffersDirectory();
        Directory cv = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(src, new IndexWriterConfig())) {
        Document doc = new Document();
        doc.add(new TextField("text", "first", Field.Store.YES));
        doc.add(new TextField("text", "second", Field.Store.YES));
        doc.add(new SortedDocValuesField("cls", new BytesRef("a")));
        w.addDocument(doc);
      }
      DirectoryReader reader = DirectoryReader.open(src);
      new DatasetSplitter(0, 0)
          .split(reader, train, test, cv, new StandardAnalyzer(), false, "cls", "text");
      try (DirectoryReader r = DirectoryReader.open(train)) {
        // expected: both values of the named field "text"; actual: only the first one
        assertEquals(
            List.of("first", "second"),
            Arrays.asList(r.storedFields().document(0).getValues("text")));
      }
    }
  }
}
