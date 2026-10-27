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

import java.util.ArrayList;
import java.util.HashMap;
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
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * SimpleNaiveBayesDocumentClassifier documents that its textFieldNames "can contain boosting
 * indication e.g. title^10". A classifier is reusable (DocumentClassifier has no one-shot
 * contract), so classifying the same document twice with the same classifier over the same reader
 * must give the same result, and the field boost must be honored on every call.
 *
 * <p>Uses public API only.
 */
public class TestSnbDocClassifierFieldBoostRepeat extends LuceneTestCase {

  private static final String CLASS = "category";
  private static final String TITLE = "title";
  private static final String BODY = "body";

  private Directory dir;
  private IndexReader reader;
  private Analyzer analyzer;
  private Map<String, Analyzer> field2analyzer;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    analyzer = new StandardAnalyzer();
    field2analyzer = new HashMap<>();
    field2analyzer.put(TITLE, analyzer);
    field2analyzer.put(BODY, analyzer);
    dir = new ByteBuffersDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
      // title words are fruit words, body words are vehicle words in the vehicle docs
      add(w, "fruit", "apple banana", "sweet juice tree");
      add(w, "fruit", "apple cherry", "sweet garden tree");
      add(w, "fruit", "banana cherry", "juice garden");
      add(w, "vehicle", "car truck", "wheel engine road fuel");
      add(w, "vehicle", "truck bus", "engine wheel fuel road");
      add(w, "vehicle", "bus car", "road engine wheel");
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
    d.add(new TextField(TITLE, title, Field.Store.YES));
    d.add(new TextField(BODY, body, Field.Store.YES));
    w.addDocument(d);
  }

  /** unseen doc: fruity title, vehicle-ish body */
  private static Document query() {
    Document d = new Document();
    d.add(new TextField(TITLE, "apple banana cherry", Field.Store.YES));
    d.add(new TextField(BODY, "engine wheel road fuel", Field.Store.YES));
    return d;
  }

  private static String render(List<ClassificationResult<BytesRef>> rs) {
    List<String> out = new ArrayList<>();
    for (ClassificationResult<BytesRef> r : rs) {
      out.add(r.assignedClass().utf8ToString() + "=" + r.score());
    }
    return out.toString();
  }

  private SimpleNaiveBayesDocumentClassifier classifier(String... fields) {
    return new SimpleNaiveBayesDocumentClassifier(reader, null, CLASS, field2analyzer, fields);
  }

  /** Same classifier, same document, two calls: results must be identical. */
  public void testSameDocTwiceSameResult() throws Exception {
    SimpleNaiveBayesDocumentClassifier boosted = classifier(TITLE + "^10", BODY);
    String first = render(boosted.getClasses(query()));
    String second = render(boosted.getClasses(query()));
    String unboosted = render(classifier(TITLE, BODY).getClasses(query()));
    System.out.println("boosted call1 = " + first);
    System.out.println("boosted call2 = " + second);
    System.out.println("unboosted     = " + unboosted);
    // precondition: the boost actually changes the scores (otherwise the test proves nothing)
    assertFalse("precondition: boost must change the scores", first.equals(unboosted));
    assertEquals("second call on the same classifier must equal the first", first, second);
  }

  /** The documented title^10 boost changes the assigned class; it must keep doing so. */
  public void testBoostHonoredOnEveryCall() throws Exception {
    String unboostedClass =
        classifier(TITLE, BODY).assignClass(query()).assignedClass().utf8ToString();
    SimpleNaiveBayesDocumentClassifier boosted = classifier(TITLE + "^10", BODY);
    String[] got = new String[3];
    for (int i = 0; i < got.length; i++) {
      got[i] = boosted.assignClass(query()).assignedClass().utf8ToString();
    }
    System.out.println(
        "unboosted=" + unboostedClass + " boosted calls=" + java.util.Arrays.toString(got));
    // precondition: on a fresh classifier the boost flips the decision
    assertFalse("precondition: boost must flip the class", got[0].equals(unboostedClass));
    for (int i = 1; i < got.length; i++) {
      assertEquals("boosted call " + (i + 1) + " must equal boosted call 1", got[0], got[i]);
    }
  }

  /** The caller's textFieldNames array is input; it should not be rewritten. */
  public void testCallerArrayUntouched() throws Exception {
    String[] fields = {TITLE + "^10", BODY};
    SimpleNaiveBayesDocumentClassifier boosted = classifier(fields);
    boosted.assignClass(query());
    System.out.println("caller array after one call = " + java.util.Arrays.toString(fields));
    assertEquals(TITLE + "^10", fields[0]);
  }
}
