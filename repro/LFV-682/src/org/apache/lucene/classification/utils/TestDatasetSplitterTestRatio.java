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

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestDatasetSplitterTestRatio extends LuceneTestCase {

  public void testTestRatioAboveHalf() throws Exception {
    StandardAnalyzer analyzer = new StandardAnalyzer();
    try (Directory source = new ByteBuffersDirectory();
        Directory training = new ByteBuffersDirectory();
        Directory test = new ByteBuffersDirectory();
        Directory cv = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(source, new IndexWriterConfig(analyzer))) {
        for (int i = 0; i < 10; i++) {
          Document doc = new Document();
          doc.add(new SortedDocValuesField("class", new BytesRef("a")));
          w.addDocument(doc);
        }
      }
      DatasetSplitter splitter = new DatasetSplitter(0.8, 0);
      splitter.split(DirectoryReader.open(source), training, test, cv, analyzer, false, "class");
      try (DirectoryReader testReader = DirectoryReader.open(test)) {
        // expected 8 of the 10 documents in the test index (testRatio 0.8); actual 5
        assertEquals("test index size for testRatio 0.8", 8, testReader.numDocs());
      }
    }
  }
}
