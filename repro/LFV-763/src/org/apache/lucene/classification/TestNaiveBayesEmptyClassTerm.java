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

import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.classification.document.SimpleNaiveBayesDocumentClassifier;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * SimpleNaiveBayesClassifier (and BM25NBClassifier) skip an empty class-field term when they build
 * the list of classes ("if (next.length > 0)"), so a document whose class value is the empty string
 * is never returned as a class. CachingNaiveBayesClassifier ("A simplistic Lucene based NaiveBayes
 * classifier, with caching feature", a subclass of SimpleNaiveBayesClassifier) and
 * SimpleNaiveBayesDocumentClassifier (also a subclass) list every class term, so on the same index
 * they return, and can assign, the empty class.
 */
public class TestNaiveBayesEmptyClassTerm extends LuceneTestCase {

  private static void add(IndexWriter w, String text, String cls) throws Exception {
    Document doc = new Document();
    doc.add(new TextField("text", text, Field.Store.NO));
    doc.add(new StringField("cat", cls, Field.Store.NO));
    w.addDocument(doc);
  }

  private static TreeSet<String> classes(List<ClassificationResult<BytesRef>> l) {
    TreeSet<String> s = new TreeSet<>();
    for (ClassificationResult<BytesRef> r : l) s.add("'" + r.assignedClass().utf8ToString() + "'");
    return s;
  }

  public void testSameClassesAsSimpleNaiveBayes() throws Exception {
    Analyzer analyzer = new StandardAnalyzer();
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        add(w, "apple banana", "fruit");
        add(w, "car truck", "vehicle");
        add(w, "car bus", ""); // a document whose class value is empty
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        SimpleNaiveBayesClassifier snb =
            new SimpleNaiveBayesClassifier(reader, analyzer, null, "cat", "text");
        CachingNaiveBayesClassifier caching =
            new CachingNaiveBayesClassifier(reader, analyzer, null, "cat", "text");
        SimpleNaiveBayesDocumentClassifier documentClassifier =
            new SimpleNaiveBayesDocumentClassifier(
                reader, null, "cat", Map.of("text", analyzer), "text");
        Document input = new Document();
        input.add(new TextField("text", "car", Field.Store.NO));

        TreeSet<String> expected = classes(snb.getClasses("car")); // ['fruit', 'vehicle']
        assertEquals("['fruit', 'vehicle']", expected.toString());
        assertEquals(
            "CachingNaiveBayesClassifier.getClasses: " + caching.getClasses("car"),
            expected,
            classes(caching.getClasses("car")));
        assertEquals(
            "SimpleNaiveBayesDocumentClassifier.getClasses: " + documentClassifier.getClasses(input),
            expected,
            classes(documentClassifier.getClasses(input)));
      }
    }
  }

  public void testEmptyClassIsNotAssigned() throws Exception {
    Analyzer analyzer = new StandardAnalyzer();
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        add(w, "apple banana", "fruit");
        add(w, "car truck", "vehicle");
        add(w, "car bus", "");
        add(w, "car bus", "");
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        SimpleNaiveBayesClassifier snb =
            new SimpleNaiveBayesClassifier(reader, analyzer, null, "cat", "text");
        CachingNaiveBayesClassifier caching =
            new CachingNaiveBayesClassifier(reader, analyzer, null, "cat", "text");
        SimpleNaiveBayesDocumentClassifier documentClassifier =
            new SimpleNaiveBayesDocumentClassifier(
                reader, null, "cat", Map.of("text", analyzer), "text");
        Document input = new Document();
        input.add(new TextField("text", "car bus", Field.Store.NO));
        String snbClass = snb.assignClass("car bus").assignedClass().utf8ToString();
        assertEquals("vehicle", snbClass);
        String cachingClass = caching.assignClass("car bus").assignedClass().utf8ToString();
        String docClass = documentClassifier.assignClass(input).assignedClass().utf8ToString();
        assertEquals(
            "caching='" + cachingClass + "' document='" + docClass + "'",
            "caching='vehicle' document='vehicle'",
            "caching='" + cachingClass + "' document='" + docClass + "'");
      }
    }
  }
}
