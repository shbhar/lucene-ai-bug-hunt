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
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/** A field boost such as "title^10" must apply to every classification, not only the first. */
public class TestSimpleNaiveBayesDocumentClassifierRepeatedBoost extends LuceneTestCase {

  private Analyzer analyzer;
  private Directory dir;
  private DirectoryReader reader;
  private Map<String, Analyzer> field2analyzer;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    analyzer = new StandardAnalyzer();
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
      add(w, "apple banana", "sweet juice tree", "fruit");
      add(w, "apple cherry", "sweet garden tree", "fruit");
      add(w, "banana cherry", "juice garden", "fruit");
      add(w, "car truck", "wheel engine road fuel", "vehicle");
      add(w, "truck bus", "engine wheel fuel road", "vehicle");
      add(w, "bus car", "road engine wheel", "vehicle");
    }
    reader = DirectoryReader.open(dir);
    field2analyzer = new HashMap<>();
    field2analyzer.put("title", analyzer);
    field2analyzer.put("body", analyzer);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    analyzer.close();
    super.tearDown();
  }

  private static void add(IndexWriter w, String title, String body, String category)
      throws Exception {
    Document doc = new Document();
    doc.add(new TextField("title", title, Field.Store.NO));
    doc.add(new TextField("body", body, Field.Store.NO));
    doc.add(new StringField("category", category, Field.Store.NO));
    w.addDocument(doc);
  }

  /** The title says fruit, the body says vehicle: the title boost decides. */
  private static Document unseen() {
    Document doc = new Document();
    doc.add(new TextField("title", "apple banana cherry", Field.Store.NO));
    doc.add(new TextField("body", "engine wheel road fuel", Field.Store.NO));
    return doc;
  }

  private SimpleNaiveBayesDocumentClassifier classifier(String... textFieldNames) {
    return new SimpleNaiveBayesDocumentClassifier(
        reader, null, "category", field2analyzer, textFieldNames);
  }

  private static List<String> render(List<ClassificationResult<BytesRef>> results) {
    List<String> out = new ArrayList<>();
    for (ClassificationResult<BytesRef> r : results) {
      out.add(r.assignedClass().utf8ToString() + "=" + r.score());
    }
    return out;
  }

  public void testSameClassifierSecondCall() throws Exception {
    List<String> unboosted = render(classifier("title", "body").getClasses(unseen()));
    SimpleNaiveBayesDocumentClassifier boosted = classifier("title^10", "body");
    List<String> first = render(boosted.getClasses(unseen()));
    assertNotEquals("precondition: the boost changes the scores", unboosted, first);
    List<String> second = render(boosted.getClasses(unseen()));
    assertEquals("second call, same classifier, same document", first, second);
  }

  public void testSecondClassifierFromSameArray() throws Exception {
    String[] textFieldNames = {"title^10", "body"};
    BytesRef first = classifier(textFieldNames).assignClass(unseen()).assignedClass();
    assertEquals("fruit", first.utf8ToString());
    BytesRef second = classifier(textFieldNames).assignClass(unseen()).assignedClass();
    assertEquals("new classifier, same configuration array", "fruit", second.utf8ToString());
  }
}
