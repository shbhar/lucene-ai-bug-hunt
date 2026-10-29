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
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestSimpleNaiveBayesDocumentClassifierBoostReuse extends LuceneTestCase {

  private static Document doc(String title, String body) {
    Document doc = new Document();
    doc.add(new TextField("title", title, Field.Store.NO));
    doc.add(new TextField("body", body, Field.Store.NO));
    return doc;
  }

  private static Document doc(String title, String body, String cls) {
    Document doc = doc(title, body);
    doc.add(new StringField("cls", cls, Field.Store.NO));
    return doc;
  }

  public void testFieldBoostAppliesOnEveryCall() throws Exception {
    Analyzer analyzer = new StandardAnalyzer();
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        w.addDocument(doc("apple", "seed", "fruit"));
        w.addDocument(doc("car", "wheel", "vehicle"));
        w.addDocument(doc("bus", "wheel", "vehicle"));
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        SimpleNaiveBayesDocumentClassifier classifier =
            new SimpleNaiveBayesDocumentClassifier(
                reader, null, "cls", Map.of("title", analyzer, "body", analyzer), "title^10", "body");
        ClassificationResult<BytesRef> first = classifier.assignClass(doc("apple", "wheel"));
        ClassificationResult<BytesRef> second = classifier.assignClass(doc("apple", "wheel"));
        // expected: the title^10 boost applies on every call (same class and score); actual: the second call is unboosted
        assertEquals(
            first.assignedClass().utf8ToString() + " " + first.score(),
            second.assignedClass().utf8ToString() + " " + second.score());
      }
    }
  }
}
