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

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
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

/**
 * A field value that analyzes to no tokens (empty, stop words only, punctuation only) must not turn
 * every class score of {@link SimpleNaiveBayesDocumentClassifier} into NaN.
 */
public class TestSimpleNaiveBayesDocumentClassifierZeroTokenValue extends LuceneTestCase {

  private Analyzer analyzer;
  private Directory dir;
  private DirectoryReader reader;
  private SimpleNaiveBayesDocumentClassifier classifier;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    analyzer = new EnglishAnalyzer();
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
      w.addDocument(trainingDoc("apple", "sweet fruit", "fruit"));
      w.addDocument(trainingDoc("pear", "green fruit", "fruit"));
      w.addDocument(trainingDoc("car", "four wheels", "vehicle"));
    }
    reader = DirectoryReader.open(dir);
    classifier =
        new SimpleNaiveBayesDocumentClassifier(
            reader, null, "class", Map.of("title", analyzer, "body", analyzer), "title", "body");
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    analyzer.close();
    super.tearDown();
  }

  private static Document trainingDoc(String title, String body, String clazz) {
    Document d = new Document();
    d.add(new TextField("title", title, Field.Store.NO));
    d.add(new TextField("body", body, Field.Store.NO));
    d.add(new StringField("class", clazz, Field.Store.YES));
    return d;
  }

  /** The document to classify: title "apple", and a body value unless {@code body} is null. */
  private static Document input(String body) {
    Document d = new Document();
    d.add(new TextField("title", "apple", Field.Store.NO));
    if (body != null) {
      d.add(new TextField("body", body, Field.Store.NO));
    }
    return d;
  }

  private void assertClassified(Document d) throws IOException {
    List<ClassificationResult<BytesRef>> classes = classifier.getClasses(d);
    ClassificationResult<BytesRef> assigned = classifier.assignClass(d);
    StringBuilder got = new StringBuilder("getClasses=[");
    double sum = 0;
    for (ClassificationResult<BytesRef> c : classes) {
      got.append(c.assignedClass().utf8ToString())
          .append('=')
          .append(String.format(Locale.ROOT, "%.4f", c.score()))
          .append(' ');
      sum += c.score();
    }
    got.append("] assignClass=")
        .append(assigned == null ? "null" : assigned.assignedClass().utf8ToString());
    for (ClassificationResult<BytesRef> c : classes) {
      assertFalse(got.toString(), Double.isNaN(c.score()));
    }
    assertEquals(got.toString(), 1.0, sum, 1e-9);
    assertNotNull(got.toString(), assigned);
  }

  public void testControls() throws Exception {
    assertClassified(input(null)); // no body value
    assertClassified(input("sweet")); // a body value with one token
  }

  public void testEmptyValue() throws Exception {
    assertClassified(input(""));
  }

  public void testStopWordsOnlyValue() throws Exception {
    assertClassified(input("To be or not to be")); // every word is an English stop word
  }

  public void testPunctuationOnlyValue() throws Exception {
    assertClassified(input("!!!")); // the tokenizer drops punctuation
  }
}
