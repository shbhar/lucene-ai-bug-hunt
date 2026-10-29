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
package org.apache.lucene.classification;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestKNearestFuzzyClassifierBoostedField extends LuceneTestCase {

  public void testBoostedFieldName() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        Analyzer analyzer = new StandardAnalyzer()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        Document doc = new Document();
        doc.add(new TextField("text", "apple", Field.Store.NO));
        doc.add(new StringField("cls", "fruit", Field.Store.YES));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        ClassificationResult<BytesRef> plain =
            new KNearestFuzzyClassifier(reader, null, analyzer, null, 1, "cls", "text")
                .assignClass("apple");
        ClassificationResult<BytesRef> boosted =
            new KNearestFuzzyClassifier(reader, null, analyzer, null, 1, "cls", "text^2")
                .assignClass("apple");
        // expected: documented "text^2" assigns the same class as "text" (fruit); actual: null
        assertEquals(
            plain.assignedClass().utf8ToString(),
            boosted == null ? null : boosted.assignedClass().utf8ToString());
      }
    }
  }
}
