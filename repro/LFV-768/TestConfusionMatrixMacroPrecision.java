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
package org.apache.lucene.classification.utils;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.classification.ClassificationResult;
import org.apache.lucene.classification.Classifier;
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
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * {@link ConfusionMatrixGenerator.ConfusionMatrix#getPrecision()} is "the macro averaged precision
 * (see getPrecision(String)) over all the classes". A class that the classifier assigns but that no
 * evaluated document has as its correct answer must be part of that average.
 */
public class TestConfusionMatrixMacroPrecision extends LuceneTestCase {

  private static final String CLASS_FIELD = "category";
  private static final String TEXT_FIELD = "text";

  /** Answers with the document's text, so each test chooses the assigned class directly. */
  private static final class EchoClassifier implements Classifier<BytesRef> {
    @Override
    public ClassificationResult<BytesRef> assignClass(String text) {
      return new ClassificationResult<>(new BytesRef(text), 1d);
    }

    @Override
    public List<ClassificationResult<BytesRef>> getClasses(String text) {
      return null;
    }

    @Override
    public List<ClassificationResult<BytesRef>> getClasses(String text, int max) {
      return null;
    }
  }

  private static void add(IndexWriter w, String correctClass, String text) throws IOException {
    Document doc = new Document();
    doc.add(new StringField(CLASS_FIELD, correctClass, Field.Store.YES));
    doc.add(new TextField(TEXT_FIELD, text, Field.Store.YES));
    w.addDocument(doc);
  }

  /** Four "sports" documents; the classifier answers "politics" for three of them. */
  public void testAssignedOnlyClassCountsInMacroPrecision() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
        add(w, "sports", "sports");
        add(w, "sports", "politics");
        add(w, "sports", "politics");
        add(w, "sports", "politics");
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        ConfusionMatrixGenerator.ConfusionMatrix cm =
            ConfusionMatrixGenerator.getConfusionMatrix(
                reader, new EchoClassifier(), CLASS_FIELD, TEXT_FIELD, -1);
        assertEquals(
            Map.of("sports", Map.of("sports", 1L, "politics", 3L)), cm.getLinearizedMatrix());
        assertEquals(1.0, cm.getPrecision("sports"), 0.0); // 1 of 1 "sports" answers is right
        assertEquals(0.0, cm.getPrecision("politics"), 0.0); // 0 of 3 "politics" answers are right
        assertEquals(
            "macro precision over sports and politics; matrix=" + cm.getLinearizedMatrix(),
            0.5,
            cm.getPrecision(),
            1e-12);
      }
    }
  }

  /**
   * The built-in naive Bayes classifier, trained on one index and evaluated on a held-out index
   * whose documents are all "sports": it answers "politics" for two of the four.
   */
  public void testHeldOutIndexWithSimpleNaiveBayes() throws Exception {
    Analyzer analyzer = new StandardAnalyzer();
    try (Directory train = newDirectory();
        Directory test = newDirectory()) {
      try (IndexWriter w = new IndexWriter(train, new IndexWriterConfig(analyzer))) {
        add(w, "sports", "football match goal striker");
        add(w, "sports", "tennis match set point");
        add(w, "sports", "goal keeper football league");
        add(w, "politics", "election vote parliament minister");
        add(w, "politics", "parliament vote budget minister");
      }
      try (IndexWriter w = new IndexWriter(test, new IndexWriterConfig(analyzer))) {
        add(w, "sports", "football goal match");
        add(w, "sports", "tennis set point");
        add(w, "sports", "federation election vote minister");
        add(w, "sports", "league vote parliament minister budget");
      }
      try (IndexReader trainReader = DirectoryReader.open(train);
          IndexReader testReader = DirectoryReader.open(test)) {
        Classifier<BytesRef> classifier =
            new SimpleNaiveBayesClassifier(trainReader, analyzer, null, CLASS_FIELD, TEXT_FIELD);
        ConfusionMatrixGenerator.ConfusionMatrix cm =
            ConfusionMatrixGenerator.getConfusionMatrix(
                testReader, classifier, CLASS_FIELD, TEXT_FIELD, -1);
        assertEquals(
            Map.of("sports", Map.of("sports", 2L, "politics", 2L)), cm.getLinearizedMatrix());
        assertEquals(1.0, cm.getPrecision("sports"), 0.0);
        assertEquals(0.0, cm.getPrecision("politics"), 0.0);
        assertEquals(
            "macro precision over sports and politics; matrix=" + cm.getLinearizedMatrix(),
            0.5,
            cm.getPrecision(),
            1e-12);
      }
    }
  }

  /**
   * Control, passes today: no assigned-only class. "b" is never assigned, so its precision is 0 and
   * it is averaged in.
   */
  public void testNeverAssignedClassIsAveragedIn() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
        add(w, "a", "a");
        add(w, "b", "a");
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        ConfusionMatrixGenerator.ConfusionMatrix cm =
            ConfusionMatrixGenerator.getConfusionMatrix(
                reader, new EchoClassifier(), CLASS_FIELD, TEXT_FIELD, -1);
        assertEquals(Map.of("a", Map.of("a", 1L), "b", Map.of("a", 1L)), cm.getLinearizedMatrix());
        assertEquals(0.5, cm.getPrecision("a"), 0.0);
        assertEquals(0.0, cm.getPrecision("b"), 0.0);
        assertEquals(0.25, cm.getPrecision(), 1e-12);
      }
    }
  }
}
