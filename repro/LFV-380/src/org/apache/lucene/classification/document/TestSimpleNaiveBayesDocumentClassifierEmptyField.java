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
package org.apache.lucene.classification.document;

import java.util.List;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.classification.ClassificationResult;
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

public class TestSimpleNaiveBayesDocumentClassifierEmptyField extends LuceneTestCase {

  public void testEmptyFieldValue() throws Exception {
    Analyzer analyzer = new StandardAnalyzer();
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        w.addDocument(
            List.of(
                new TextField("title", "apple", Field.Store.NO),
                new TextField("body", "sweet fruit", Field.Store.NO),
                new StringField("cls", "fruit", Field.Store.NO)));
        w.addDocument(
            List.of(
                new TextField("title", "car", Field.Store.NO),
                new TextField("body", "four wheels", Field.Store.NO),
                new StringField("cls", "vehicle", Field.Store.NO)));
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        SimpleNaiveBayesDocumentClassifier classifier =
            new SimpleNaiveBayesDocumentClassifier(
                reader, null, "cls", Map.of("title", analyzer, "body", analyzer), "title", "body");
        Document doc = new Document();
        doc.add(new TextField("title", "apple", Field.Store.NO));
        doc.add(new TextField("body", "", Field.Store.NO));
        ClassificationResult<BytesRef> result = classifier.assignClass(doc);
        // expected a class with a finite score; actual null, because every class score is NaN
        assertTrue(
            "expected a class with a finite score; assignClass: "
                + result
                + ", getClasses: "
                + classifier.getClasses(doc),
            result != null && Double.isFinite(result.score()));
      }
    }
  }
}
