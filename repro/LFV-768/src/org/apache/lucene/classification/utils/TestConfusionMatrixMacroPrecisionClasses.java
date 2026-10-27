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

import java.util.List;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.classification.ClassificationResult;
import org.apache.lucene.classification.Classifier;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * ConfusionMatrix#getPrecision() is documented as "the macro averaged precision (see
 * getPrecision(String)) over all the classes", but it averages only over the classes that occur as
 * a correct answer (the keys of the linearized matrix). A class that the classifier assigns but that
 * no evaluated document has is left out, so its false positives disappear from the macro average.
 */
public class TestConfusionMatrixMacroPrecisionClasses extends LuceneTestCase {

  /** answers the class stored in the document's text field */
  private static final class FixedAnswerClassifier implements Classifier<BytesRef> {
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

  private static void add(IndexWriter w, String correct, String answer) throws Exception {
    Document doc = new Document();
    doc.add(new StringField("category", correct, Field.Store.YES));
    doc.add(new StoredField("text", answer));
    w.addDocument(doc);
  }

  public void testPredictedOnlyClassCountsInMacroPrecision() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
        // four documents of class "sports"; the classifier answers "politics" for three of them
        add(w, "sports", "sports");
        add(w, "sports", "politics");
        add(w, "sports", "politics");
        add(w, "sports", "politics");
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        ConfusionMatrixGenerator.ConfusionMatrix cm =
            ConfusionMatrixGenerator.getConfusionMatrix(
                reader, new FixedAnswerClassifier(), "category", "text", -1);
        assertEquals(4, cm.getNumberOfEvaluatedDocs());
        // per-class precision is right: sports 1/1, politics 0/3
        assertEquals(1.0, cm.getPrecision("sports"), 0.0);
        assertEquals(0.0, cm.getPrecision("politics"), 0.0);
        // macro average over all the classes of the matrix (sports, politics) = (1 + 0) / 2
        assertEquals(
            "macro precision must average getPrecision(String) over all the classes, "
                + "including 'politics', which the classifier answered three times; matrix="
                + cm.getLinearizedMatrix(),
            0.5,
            cm.getPrecision(),
            1e-12);
      }
    }
  }

  public void testMacroPrecisionMatchesAverageOfPerClassPrecision() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
        // two correct classes; the classifier also answers a third class "c" that no document has
        add(w, "a", "a");
        add(w, "a", "c");
        add(w, "b", "b");
        add(w, "b", "c");
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        ConfusionMatrixGenerator.ConfusionMatrix cm =
            ConfusionMatrixGenerator.getConfusionMatrix(
                reader, new FixedAnswerClassifier(), "category", "text", -1);
        double avg = (cm.getPrecision("a") + cm.getPrecision("b") + cm.getPrecision("c")) / 3;
        assertEquals(1.0, cm.getPrecision("a"), 0.0);
        assertEquals(1.0, cm.getPrecision("b"), 0.0);
        assertEquals(0.0, cm.getPrecision("c"), 0.0);
        assertEquals(
            "macro precision over the classes a, b, c; matrix=" + cm.getLinearizedMatrix(),
            avg,
            cm.getPrecision(),
            1e-12);
      }
    }
  }
}
