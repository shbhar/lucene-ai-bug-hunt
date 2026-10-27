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
import org.apache.lucene.classification.SimpleNaiveBayesClassifier;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenFilter;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * Independent check: a document whose text field has a value that analyzes to zero tokens (empty
 * string, stop words only, punctuation only) should still get a class and finite scores in [0,1]
 * from SimpleNaiveBayesDocumentClassifier. Public API only.
 */
public class TestNaiveBayesDocZeroTokenValue extends LuceneTestCase {

  private static final String CLASS = "category";
  private static final String TITLE = "title";
  private static final String BODY = "body";

  private Directory dir;
  private IndexReader reader;
  private Analyzer analyzer;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    // letters-only tokenizer, lowercasing, English stop set
    analyzer =
        new MockAnalyzer(random(), MockTokenizer.SIMPLE, true, MockTokenFilter.ENGLISH_STOPSET);
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
      add(w, "fruit", "apple banana", "sweet juicy fruit");
      add(w, "fruit", "apple pear", "ripe red fruit");
      add(w, "vehicle", "car truck", "fast engine wheels");
      add(w, "vehicle", "bus car", "road engine");
      w.commit();
    }
    reader = DirectoryReader.open(dir);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    analyzer.close();
    super.tearDown();
  }

  private static void add(IndexWriter w, String cls, String title, String body) throws Exception {
    Document d = new Document();
    d.add(new StringField(CLASS, cls, Field.Store.YES));
    d.add(new TextField(TITLE, title, Field.Store.NO));
    d.add(new TextField(BODY, body, Field.Store.NO));
    w.addDocument(d);
  }

  private SimpleNaiveBayesDocumentClassifier newClassifier(String... fields) {
    Map<String, Analyzer> f2a = new HashMap<>();
    f2a.put(TITLE, analyzer);
    f2a.put(BODY, analyzer);
    return new SimpleNaiveBayesDocumentClassifier(reader, null, CLASS, f2a, fields);
  }

  private static Document doc(String title, String body) {
    Document d = new Document();
    d.add(new TextField(TITLE, title, Field.Store.NO));
    if (body != null) {
      d.add(new TextField(BODY, body, Field.Store.NO));
    }
    return d;
  }

  /** Asserts the classification result is a real class with finite normalised scores. */
  private void check(String label, Document d, String expectedClass) throws Exception {
    SimpleNaiveBayesDocumentClassifier c = newClassifier(TITLE, BODY);
    List<ClassificationResult<BytesRef>> all = c.getClasses(d);
    StringBuilder sb = new StringBuilder(label).append(": getClasses=");
    for (ClassificationResult<BytesRef> r : all) {
      sb.append(r.assignedClass().utf8ToString()).append('=').append(r.score()).append(' ');
    }
    ClassificationResult<BytesRef> assigned = newClassifier(TITLE, BODY).assignClass(d);
    sb.append("assignClass=")
        .append(assigned == null ? "null" : assigned.assignedClass().utf8ToString());
    System.out.println(sb);

    assertEquals(label + ": two classes expected", 2, all.size());
    double sum = 0;
    for (ClassificationResult<BytesRef> r : all) {
      double s = r.score();
      assertFalse(label + ": score is NaN for " + r.assignedClass().utf8ToString(), Double.isNaN(s));
      assertTrue(label + ": score out of [0,1]: " + s, s >= 0 && s <= 1);
      sum += s;
    }
    assertEquals(label + ": normalised scores should sum to 1", 1.0, sum, 1e-9);
    assertNotNull(label + ": assignClass returned null", assigned);
    assertEquals(label, expectedClass, assigned.assignedClass().utf8ToString());
  }

  // ---- controls (expected to pass) ----

  public void testControlBodyAbsent() throws Exception {
    check("body absent", doc("apple", null), "fruit");
  }

  public void testControlBodyWithTokens() throws Exception {
    check("body with tokens", doc("apple", "sweet fruit"), "fruit");
  }

  /** The text (String) Naive Bayes classifier accepts zero-token input and returns finite scores. */
  public void testControlTextClassifierZeroTokens() throws Exception {
    SimpleNaiveBayesClassifier tc =
        new SimpleNaiveBayesClassifier(reader, analyzer, null, CLASS, TITLE, BODY);
    List<ClassificationResult<BytesRef>> all = tc.getClasses("the of and");
    for (ClassificationResult<BytesRef> r : all) {
      System.out.println("text classifier zero tokens: " + r.assignedClass().utf8ToString() + "=" + r.score());
      assertFalse(Double.isNaN(r.score()));
    }
    assertNotNull(tc.assignClass(""));
  }

  // ---- the claim (expected to fail if the bug is present) ----

  public void testBodyEmptyString() throws Exception {
    check("body empty string", doc("apple", ""), "fruit");
  }

  public void testBodyStopWordsOnly() throws Exception {
    check("body stop words only", doc("apple", "the of and"), "fruit");
  }

  public void testBodyPunctuationOnly() throws Exception {
    check("body punctuation only", doc("apple", "!!! ... 123"), "fruit");
  }
}
