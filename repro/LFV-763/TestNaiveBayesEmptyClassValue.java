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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
 * The four Naive Bayes classifiers on one training index in which unlabelled documents carry an
 * empty class value (StringField("cat", "")). SimpleNaiveBayesClassifier and BM25NBClassifier skip
 * the empty class term; the subclasses of SimpleNaiveBayesClassifier, CachingNaiveBayesClassifier
 * and SimpleNaiveBayesDocumentClassifier, offer it as a class and can assign it. The tests only ask
 * that the subclasses agree with their parent, so they pass with either fix direction.
 */
public class TestNaiveBayesEmptyClassValue extends LuceneTestCase {

  private final Analyzer analyzer = new StandardAnalyzer();

  /** fruit "apple banana", vehicle "car truck", and n unlabelled "car bus" documents. */
  private IndexReader index(Directory dir, int unlabelled, boolean emptyClassValue)
      throws Exception {
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
      w.addDocument(doc("apple banana", "fruit"));
      w.addDocument(doc("car truck", "vehicle"));
      for (int i = 0; i < unlabelled; i++) {
        w.addDocument(doc("car bus", emptyClassValue ? "" : null));
      }
    }
    return DirectoryReader.open(dir);
  }

  private static Document doc(String text, String cls) {
    Document doc = new Document();
    doc.add(new TextField("text", text, Field.Store.NO));
    if (cls != null) {
      doc.add(new StringField("cat", cls, Field.Store.NO));
    }
    return doc;
  }

  private static Document input(String text) {
    Document doc = new Document();
    doc.add(new TextField("text", text, Field.Store.NO));
    return doc;
  }

  /** The class names, sorted, e.g. ['', 'fruit', 'vehicle']. */
  private static String names(List<ClassificationResult<BytesRef>> results) {
    TreeSet<String> s = new TreeSet<>();
    for (ClassificationResult<BytesRef> r : results) {
      s.add("'" + r.assignedClass().utf8ToString() + "'");
    }
    return s.toString();
  }

  /** The classes with their scores, best first, e.g. ['' 0.40, 'vehicle' 0.40, 'fruit' 0.20]. */
  private static String scores(List<ClassificationResult<BytesRef>> results) {
    List<String> s = new ArrayList<>();
    for (ClassificationResult<BytesRef> r : results) {
      s.add(String.format(Locale.ROOT, "'%s' %.2f", r.assignedClass().utf8ToString(), r.score()));
    }
    return s.toString();
  }

  /** {caching=..., document=...}, so a failure shows both subclasses side by side. */
  private static Map<String, String> subclasses(String caching, String document) {
    Map<String, String> m = new LinkedHashMap<>();
    m.put("caching", caching);
    m.put("document", document);
    return m;
  }

  /** One document with class value "": the subclasses list an extra class ''. */
  public void testSubclassesOfferTheSameClassesAsTheirParent() throws Exception {
    try (Directory dir = newDirectory();
        IndexReader reader = index(dir, 1, true)) {
      List<ClassificationResult<BytesRef>> parent =
          new SimpleNaiveBayesClassifier(reader, analyzer, null, "cat", "text").getClasses("car");
      List<ClassificationResult<BytesRef>> bm25 =
          new BM25NBClassifier(reader, analyzer, null, "cat", "text").getClasses("car");
      List<ClassificationResult<BytesRef>> caching =
          new CachingNaiveBayesClassifier(reader, analyzer, null, "cat", "text").getClasses("car");
      List<ClassificationResult<BytesRef>> document =
          new SimpleNaiveBayesDocumentClassifier(
                  reader, null, "cat", Map.of("text", analyzer), "text")
              .getClasses(input("car"));
      assertEquals("BM25NBClassifier " + scores(bm25), names(parent), names(bm25));
      assertEquals(
          "getClasses(\"car\"): SimpleNaiveBayesClassifier "
              + scores(parent)
              + ", CachingNaiveBayesClassifier "
              + scores(caching)
              + ", SimpleNaiveBayesDocumentClassifier "
              + scores(document),
          subclasses(names(parent), names(parent)),
          subclasses(names(caching), names(document)));
    }
  }

  /**
   * Two documents with class value "": the subclasses assign '' where the parent assigns 'vehicle'.
   */
  public void testSubclassesAssignTheSameClassAsTheirParent() throws Exception {
    try (Directory dir = newDirectory();
        IndexReader reader = index(dir, 2, true)) {
      String parent =
          new SimpleNaiveBayesClassifier(reader, analyzer, null, "cat", "text")
              .assignClass("car bus")
              .assignedClass()
              .utf8ToString();
      String bm25 =
          new BM25NBClassifier(reader, analyzer, null, "cat", "text")
              .assignClass("car bus")
              .assignedClass()
              .utf8ToString();
      String caching =
          new CachingNaiveBayesClassifier(reader, analyzer, null, "cat", "text")
              .assignClass("car bus")
              .assignedClass()
              .utf8ToString();
      String document =
          new SimpleNaiveBayesDocumentClassifier(
                  reader, null, "cat", Map.of("text", analyzer), "text")
              .assignClass(input("car bus"))
              .assignedClass()
              .utf8ToString();
      // BM25NBClassifier scores differently, so only its class set is compared (first test)
      assertEquals(
          "assignClass(\"car bus\"): SimpleNaiveBayesClassifier '"
              + parent
              + "', BM25NBClassifier '"
              + bm25
              + "'",
          subclasses("'" + parent + "'", "'" + parent + "'"),
          subclasses("'" + caching + "'", "'" + document + "'"));
    }
  }

  /**
   * Control: the same unlabelled documents without a class field. The parent and both subclasses
   * agree (passes today).
   */
  public void testControlUnlabelledDocumentsWithoutClassField() throws Exception {
    try (Directory dir = newDirectory();
        IndexReader reader = index(dir, 2, false)) {
      SimpleNaiveBayesClassifier parent =
          new SimpleNaiveBayesClassifier(reader, analyzer, null, "cat", "text");
      CachingNaiveBayesClassifier caching =
          new CachingNaiveBayesClassifier(reader, analyzer, null, "cat", "text");
      SimpleNaiveBayesDocumentClassifier document =
          new SimpleNaiveBayesDocumentClassifier(
              reader, null, "cat", Map.of("text", analyzer), "text");
      assertEquals("['fruit', 'vehicle']", names(parent.getClasses("car")));
      assertEquals(names(parent.getClasses("car")), names(caching.getClasses("car")));
      assertEquals(names(parent.getClasses("car")), names(document.getClasses(input("car"))));
      String expected = parent.assignClass("car bus").assignedClass().utf8ToString();
      assertEquals(expected, caching.assignClass("car bus").assignedClass().utf8ToString());
      assertEquals(expected, document.assignClass(input("car bus")).assignedClass().utf8ToString());
    }
  }
}
