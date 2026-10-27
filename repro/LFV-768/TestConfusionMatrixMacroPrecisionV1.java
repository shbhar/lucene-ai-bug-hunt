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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.apache.lucene.classification.ClassificationResult;
import org.apache.lucene.classification.Classifier;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * Independent check of ConfusionMatrix.getPrecision() (macro average). Only public API is used:
 * documents are indexed, a fixed lookup classifier answers, and the matrix comes from
 * ConfusionMatrixGenerator.getConfusionMatrix.
 */
public class TestConfusionMatrixMacroPrecisionV1 extends LuceneTestCase {

  private static final String CLASS_FIELD = "cat";
  private static final String TEXT_FIELD = "body";

  /** Classifier that answers from a fixed text -> label table. */
  private static Classifier<BytesRef> lookupClassifier(Map<String, String> answers) {
    return new Classifier<BytesRef>() {
      @Override
      public ClassificationResult<BytesRef> assignClass(String text) {
        String label = answers.get(text);
        return label == null ? null : new ClassificationResult<>(new BytesRef(label), 1d);
      }

      @Override
      public List<ClassificationResult<BytesRef>> getClasses(String text) {
        throw new UnsupportedOperationException();
      }

      @Override
      public List<ClassificationResult<BytesRef>> getClasses(String text, int max) {
        throw new UnsupportedOperationException();
      }
    };
  }

  /** gold[i] is the correct class of document i, pred[i] the classifier's answer for it. */
  private static ConfusionMatrixGenerator.ConfusionMatrix matrix(String[] gold, String[] pred)
      throws IOException {
    assertEquals(gold.length, pred.length);
    Map<String, String> answers = new HashMap<>();
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(new MockAnalyzer(random())))) {
      for (int i = 0; i < gold.length; i++) {
        String text = "text" + i;
        answers.put(text, pred[i]);
        Document doc = new Document();
        doc.add(new StringField(CLASS_FIELD, gold[i], Field.Store.YES));
        doc.add(new TextField(TEXT_FIELD, text, Field.Store.YES));
        w.addDocument(doc);
      }
      w.commit();
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        ConfusionMatrixGenerator.ConfusionMatrix cm =
            ConfusionMatrixGenerator.getConfusionMatrix(
                reader, lookupClassifier(answers), CLASS_FIELD, TEXT_FIELD, -1);
        assertEquals(gold.length, cm.getNumberOfEvaluatedDocs());
        return cm;
      }
    }
  }

  /** Reference per-class precision tp/(tp+fp), 0 when the class was never assigned. */
  private static double refPrecision(String klass, String[] gold, String[] pred) {
    int tp = 0;
    int assigned = 0;
    for (int i = 0; i < gold.length; i++) {
      if (pred[i].equals(klass)) {
        assigned++;
        if (gold[i].equals(klass)) {
          tp++;
        }
      }
    }
    return assigned == 0 ? 0d : (double) tp / assigned;
  }

  private static double refMacroPrecision(TreeSet<String> classes, String[] gold, String[] pred) {
    double sum = 0;
    for (String k : classes) {
      sum += refPrecision(k, gold, pred);
    }
    return sum / classes.size();
  }

  private static TreeSet<String> labels(String[]... arrays) {
    TreeSet<String> s = new TreeSet<>();
    for (String[] a : arrays) {
      for (String x : a) {
        s.add(x);
      }
    }
    return s;
  }

  /**
   * Classes a and b, two documents each. Each class gets one right answer and one wrong answer
   * 'x', a label no document has. Half of the answers are wrong, and 'x' was assigned twice and
   * was wrong both times (getPrecision("x") is 0), so the macro average over the classes a, b, x is
   * (1 + 1 + 0) / 3.
   */
  public void testWrongAnswersIntoUnseenLabel() throws IOException {
    String[] gold = {"a", "a", "b", "b"};
    String[] pred = {"a", "x", "b", "x"};
    ConfusionMatrixGenerator.ConfusionMatrix cm = matrix(gold, pred);

    Map<String, Map<String, Long>> expectedMatrix = new HashMap<>();
    expectedMatrix.put("a", Map.of("a", 1L, "x", 1L));
    expectedMatrix.put("b", Map.of("b", 1L, "x", 1L));
    assertEquals(expectedMatrix, cm.getLinearizedMatrix());

    // per-class precision is right, including the assigned-only class x
    assertEquals(1d, cm.getPrecision("a"), 1e-12);
    assertEquals(1d, cm.getPrecision("b"), 1e-12);
    assertEquals(0d, cm.getPrecision("x"), 1e-12);

    double expected = (cm.getPrecision("a") + cm.getPrecision("b") + cm.getPrecision("x")) / 3;
    assertEquals(
        "macro precision over the classes {a, b, x}; matrix="
            + cm.getLinearizedMatrix()
            + " F1="
            + cm.getF1Measure()
            + " recall="
            + cm.getRecall(),
        expected,
        cm.getPrecision(),
        1e-12);
  }

  /**
   * Control: the same data, but the wrong answers go to the other existing class. No label is
   * assigned-only, and macro precision is (1/2 + 1/2) / 2 = 0.5. This passes on the code under test.
   */
  public void testControlWrongAnswersIntoExistingLabel() throws IOException {
    String[] gold = {"a", "a", "b", "b"};
    String[] pred = {"a", "b", "b", "a"};
    ConfusionMatrixGenerator.ConfusionMatrix cm = matrix(gold, pred);
    assertEquals(0.5d, cm.getPrecision("a"), 1e-12);
    assertEquals(0.5d, cm.getPrecision("b"), 1e-12);
    assertEquals("matrix=" + cm.getLinearizedMatrix(), 0.5d, cm.getPrecision(), 1e-12);
  }

  /**
   * Random datasets (gold in {a,b,c}, answers in {a,b,c,d}): compares getPrecision() with an
   * independent reference that averages tp/(tp+fp) over the union of the correct and the assigned
   * labels. Also records whether the result equals the average over the correct labels only.
   */
  public void testRandomDatasetsAgainstReference() throws IOException {
    String[] goldLabels = {"a", "b", "c"};
    String[] predLabels = {"a", "b", "c", "d"};
    int datasets = 150;
    int mismatches = 0;
    int mismatchesWithoutAssignedOnly = 0;
    int withAssignedOnly = 0;
    int equalsGoldOnlyMean = 0;
    int implementationLower = 0;
    List<String> examples = new ArrayList<>();
    for (int t = 0; t < datasets; t++) {
      int n = 1 + random().nextInt(7);
      String[] gold = new String[n];
      String[] pred = new String[n];
      for (int i = 0; i < n; i++) {
        gold[i] = goldLabels[random().nextInt(goldLabels.length)];
        pred[i] = predLabels[random().nextInt(predLabels.length)];
      }
      ConfusionMatrixGenerator.ConfusionMatrix cm = matrix(gold, pred);
      double actual = cm.getPrecision();
      TreeSet<String> goldSet = labels(gold);
      TreeSet<String> union = labels(gold, pred);
      boolean assignedOnly = union.size() > goldSet.size();
      if (assignedOnly) {
        withAssignedOnly++;
      }
      double refUnion = refMacroPrecision(union, gold, pred);
      double refGold = refMacroPrecision(goldSet, gold, pred);
      if (Math.abs(actual - refGold) < 1e-12) {
        equalsGoldOnlyMean++;
      }
      if (Math.abs(actual - refUnion) > 1e-12) {
        mismatches++;
        if (!assignedOnly) {
          mismatchesWithoutAssignedOnly++;
        }
        if (actual < refUnion) {
          implementationLower++;
        }
        if (examples.size() < 3) {
          examples.add(
              "matrix=" + cm.getLinearizedMatrix() + " got=" + actual + " expected=" + refUnion);
        }
      }
    }
    String summary =
        "datasets="
            + datasets
            + " withAssignedOnlyLabel="
            + withAssignedOnly
            + " mismatches="
            + mismatches
            + " mismatchesWithoutAssignedOnlyLabel="
            + mismatchesWithoutAssignedOnly
            + " implementationLowerThanReference="
            + implementationLower
            + " equalsCorrectLabelsOnlyMean="
            + equalsGoldOnlyMean
            + " examples="
            + examples;
    System.out.println("V1-SUMMARY " + summary);
    assertEquals(summary, 0, mismatches);
  }
}
