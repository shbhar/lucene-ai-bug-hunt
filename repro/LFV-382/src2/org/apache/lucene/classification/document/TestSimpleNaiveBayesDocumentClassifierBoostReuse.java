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

import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * SimpleNaiveBayesDocumentClassifier documents that textFieldNames "can contain boosting indication e.g.
 * title^10". analyzeSeedDocument parses the boost and then writes the bare field name back into the
 * textFieldNames array (SimpleNaiveBayesDocumentClassifier.java:170). From the second classification on, the
 * boost is gone, so the same document gets a different class. The array is the caller's varargs array, so the
 * caller's array is rewritten too.
 */
public class TestSimpleNaiveBayesDocumentClassifierBoostReuse extends LuceneTestCase {

  private static void add(IndexWriter w, String title, String body, String cls) throws Exception {
    Document d = new Document();
    d.add(new TextField("title", title, Field.Store.YES));
    d.add(new TextField("body", body, Field.Store.YES));
    d.add(new StringField("cls", cls, Field.Store.YES));
    w.addDocument(d);
  }

  private static Document query() {
    Document q = new Document();
    q.add(new TextField("title", "apple", Field.Store.NO)); // title evidence: fruit
    q.add(new TextField("body", "wheel", Field.Store.NO)); // body evidence: vehicle
    return q;
  }

  public void testBoostIsKeptAcrossCalls() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        add(w, "apple", "engine", "fruit");
        add(w, "pear", "engine wheel", "fruit");
        add(w, "car", "engine wheel", "vehicle");
        add(w, "bus", "engine wheel", "vehicle");
        add(w, "truck", "wheel", "vehicle");
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        Map<String, Analyzer> field2analyzer = new HashMap<>();
        field2analyzer.put("title", analyzer);
        field2analyzer.put("body", analyzer);
        String[] textFieldNames = {"title^10", "body"};
        SimpleNaiveBayesDocumentClassifier classifier =
            new SimpleNaiveBayesDocumentClassifier(reader, null, "cls", field2analyzer, textFieldNames);

        String first = classifier.assignClass(query()).getAssignedClass().utf8ToString();
        String second = classifier.assignClass(query()).getAssignedClass().utf8ToString();
        assertEquals("boosted title decides the class", "fruit", first);
        assertEquals("classifying the same document again must give the same class", first, second);
      }
    }
  }

  public void testCallerArrayNotModified() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        add(w, "apple", "engine", "fruit");
        add(w, "car", "wheel", "vehicle");
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        Map<String, Analyzer> field2analyzer = new HashMap<>();
        field2analyzer.put("title", analyzer);
        field2analyzer.put("body", analyzer);
        String[] textFieldNames = {"title^10", "body"};
        SimpleNaiveBayesDocumentClassifier classifier =
            new SimpleNaiveBayesDocumentClassifier(reader, null, "cls", field2analyzer, textFieldNames);
        classifier.assignClass(query());
        assertEquals("caller's textFieldNames array was rewritten", "title^10", textFieldNames[0]);
      }
    }
  }
}
