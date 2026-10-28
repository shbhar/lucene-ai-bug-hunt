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

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.classification.document.SimpleNaiveBayesDocumentClassifier;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * Independent check: SimpleNaiveBayesClassifier skips an empty class-field term. Do its two
 * subclasses (CachingNaiveBayesClassifier, SimpleNaiveBayesDocumentClassifier) offer the same class
 * set on the same index, and do they ever assign the empty class?
 */
public class TestEmptyClassValue extends LuceneTestCase {

  private static final String TEXT = "body";
  private static final String CLS = "label";

  private static Document doc(String text, String label) {
    Document d = new Document();
    d.add(new TextField(TEXT, text, Field.Store.YES));
    if (label != null) {
      d.add(new StringField(CLS, label, Field.Store.YES));
    }
    return d;
  }

  private static Document query(String text) {
    Document d = new Document();
    d.add(new TextField(TEXT, text, Field.Store.NO));
    return d;
  }

  private static Set<String> classSet(List<ClassificationResult<BytesRef>> results) {
    Set<String> s = new TreeSet<>();
    for (ClassificationResult<BytesRef> r : results) {
      s.add(cls(r));
    }
    return s;
  }

  // the only API that differs between 9.x (getAssignedClass) and 10.x (record accessor)
  private static String cls(ClassificationResult<BytesRef> r) {
    return r.assignedClass().utf8ToString();
  }

  private interface Body {
    void run(IndexReader reader, Analyzer analyzer) throws IOException;
  }

  /** emptyLabelled: true -> two documents carry the class value ""; false -> they omit the field. */
  private void withIndex(boolean emptyLabelled, Body body) throws IOException {
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, true);
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        w.addDocument(doc("car truck", "vehicle"));
        w.addDocument(doc("apple pear", "fruit"));
        w.addDocument(doc("car", emptyLabelled ? "" : null));
        w.addDocument(doc("car", emptyLabelled ? "" : null));
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        if (emptyLabelled) {
          // the empty value is indexed as a real (zero-length) term by a stock StringField
          assertEquals(2, reader.docFreq(new Term(CLS, new BytesRef())));
        }
        body.run(reader, analyzer);
      }
    }
    analyzer.close();
  }

  /** Control: unlabelled documents omit the class field; all three classifiers agree. */
  public void testControlNoEmptyValue() throws IOException {
    withIndex(
        false,
        (reader, analyzer) -> {
          Set<String> expected = Set.of("fruit", "vehicle");
          assertEquals(
              expected,
              classSet(
                  new SimpleNaiveBayesClassifier(reader, analyzer, null, CLS, TEXT)
                      .getClasses("car")));
          assertEquals(
              expected,
              classSet(
                  new CachingNaiveBayesClassifier(reader, analyzer, null, CLS, TEXT)
                      .getClasses("car")));
          assertEquals(
              expected,
              classSet(
                  new SimpleNaiveBayesDocumentClassifier(
                          reader, null, CLS, Map.of(TEXT, analyzer), TEXT)
                      .getClasses(query("car"))));
        });
  }

  /** Same index with "" instead of a missing field: the subclasses should keep the parent's class set. */
  public void testClassSetOfSubclassesMatchesParent() throws IOException {
    withIndex(
        true,
        (reader, analyzer) -> {
          Set<String> parent =
              classSet(
                  new SimpleNaiveBayesClassifier(reader, analyzer, null, CLS, TEXT)
                      .getClasses("car"));
          // parent skips the empty term (SimpleNaiveBayesClassifier: if (next.length > 0))
          assertEquals(Set.of("fruit", "vehicle"), parent);
          Set<String> caching =
              classSet(
                  new CachingNaiveBayesClassifier(reader, analyzer, null, CLS, TEXT)
                      .getClasses("car"));
          Set<String> document =
              classSet(
                  new SimpleNaiveBayesDocumentClassifier(
                          reader, null, CLS, Map.of(TEXT, analyzer), TEXT)
                      .getClasses(query("car")));
          assertEquals(
              "class sets: parent=" + parent + " caching=" + caching + " document=" + document,
              "caching=" + parent + " document=" + parent,
              "caching=" + caching + " document=" + document);
        });
  }

  /** None of the Naive Bayes classifiers should assign the class "" that the parent never offers. */
  public void testEmptyClassNeverAssigned() throws IOException {
    withIndex(
        true,
        (reader, analyzer) -> {
          String parent =
              cls(
                  new SimpleNaiveBayesClassifier(reader, analyzer, null, CLS, TEXT)
                      .assignClass("car"));
          assertEquals("vehicle", parent);
          String caching =
              cls(
                  new CachingNaiveBayesClassifier(reader, analyzer, null, CLS, TEXT)
                      .assignClass("car"));
          String document =
              cls(
                  new SimpleNaiveBayesDocumentClassifier(
                          reader, null, CLS, Map.of(TEXT, analyzer), TEXT)
                      .assignClass(query("car")));
          assertEquals(
              "parent assigns '" + parent + "'",
              "caching='vehicle' document='vehicle'",
              "caching='" + caching + "' document='" + document + "'");
        });
  }
}
