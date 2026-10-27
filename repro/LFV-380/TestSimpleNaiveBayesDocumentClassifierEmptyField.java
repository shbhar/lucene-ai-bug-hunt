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
import java.util.List;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.classification.ClassificationResult;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenFilter;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * SimpleNaiveBayesDocumentClassifier.calculateLogLikelihood returns {@code result / tokenizedText.length}
 * (SimpleNaiveBayesDocumentClassifier.java:222). A field value that analyses to no tokens (an empty value, or a
 * value made only of stop words) gives 0.0 / 0 = NaN. That NaN is added to every class score, the normalisation
 * turns every probability into NaN, and assignClass returns null although the other field has evidence.
 */
public class TestSimpleNaiveBayesDocumentClassifierEmptyField extends LuceneTestCase {

  private static void add(IndexWriter w, String title, String body, String cls) throws Exception {
    Document d = new Document();
    d.add(new TextField("title", title, Field.Store.YES));
    d.add(new TextField("body", body, Field.Store.YES));
    d.add(new StringField("cls", cls, Field.Store.YES));
    w.addDocument(d);
  }

  private static void check(SimpleNaiveBayesDocumentClassifier classifier, Document doc) throws Exception {
    List<ClassificationResult<BytesRef>> classes = classifier.getClasses(doc);
    double sum = 0;
    for (ClassificationResult<BytesRef> c : classes) {
      assertFalse("probability of " + c.assignedClass().utf8ToString() + " is NaN", Double.isNaN(c.score()));
      sum += c.score();
    }
    assertEquals("probabilities sum to 1", 1.0, sum, 1e-9);
    ClassificationResult<BytesRef> assigned = classifier.assignClass(doc);
    assertNotNull("assignClass returned null", assigned);
    assertEquals("fruit", assigned.assignedClass().utf8ToString());
  }

  private void run(Analyzer analyzer, String bodyValue) throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        add(w, "apple", "sweet fruit", "fruit");
        add(w, "pear", "green fruit", "fruit");
        add(w, "car", "four wheels", "vehicle");
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        Map<String, Analyzer> field2analyzer = new HashMap<>();
        field2analyzer.put("title", analyzer);
        field2analyzer.put("body", analyzer);
        SimpleNaiveBayesDocumentClassifier classifier =
            new SimpleNaiveBayesDocumentClassifier(reader, null, "cls", field2analyzer, "title", "body");
        Document withoutBody = new Document();
        withoutBody.add(new TextField("title", "apple", Field.Store.NO));
        check(classifier, withoutBody); // control: passes

        Document emptyBody = new Document();
        emptyBody.add(new TextField("title", "apple", Field.Store.NO));
        emptyBody.add(new TextField("body", bodyValue, Field.Store.NO));
        check(classifier, emptyBody);
      }
    }
  }

  public void testEmptyFieldValue() throws Exception {
    run(new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false), "");
  }

  public void testStopWordsOnlyFieldValue() throws Exception {
    run(
        new MockAnalyzer(random(), MockTokenizer.WHITESPACE, true, MockTokenFilter.ENGLISH_STOPSET),
        "the of and");
  }
}
