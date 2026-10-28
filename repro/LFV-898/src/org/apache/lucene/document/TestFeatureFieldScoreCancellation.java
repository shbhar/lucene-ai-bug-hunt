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
package org.apache.lucene.document;

import java.io.IOException;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * FeatureField.newSaturationQuery documents the score weight * S / (S + pivot) and
 * newSigmoidQuery documents weight * S^a / (S^a + pivot^a). Both are positive and strictly
 * increasing in S. The implementation evaluates them as weight * (1 - pivot / (S + pivot)) (in
 * float) and weight * (1 - pivot^a / (S^a + pivot^a)) (in double), and the subtraction cancels
 * when S is far below the pivot: matching documents score exactly 0 and documents with different
 * feature values tie. Public API only; the stored (9-bit) value S of each document is read back
 * with newLinearQuery(weight 1), which scores w * S.
 */
public class TestFeatureFieldScoreCancellation extends LuceneTestCase {

  private static float documentedSaturation(double w, double s, double k) {
    return (float) (w * s / (s + k));
  }

  private static float documentedSigmoid(double w, double s, double k, double a) {
    return (float) (w * Math.pow(s, a) / (Math.pow(s, a) + Math.pow(k, a)));
  }

  private static DirectoryReader index(Directory dir, float... values) throws IOException {
    try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
      for (float v : values) {
        Document doc = new Document();
        doc.add(new FeatureField("features", "pop", v));
        w.addDocument(doc);
      }
      w.forceMerge(1);
    }
    return DirectoryReader.open(dir);
  }

  /** Stored feature value of every document, read back with the documented linear function. */
  private static float[] storedValues(IndexSearcher searcher, int maxDoc) throws IOException {
    float[] s = new float[maxDoc];
    for (ScoreDoc sd :
        searcher.search(FeatureField.newLinearQuery("features", "pop", 1f), maxDoc).scoreDocs) {
      s[sd.doc] = sd.score;
    }
    return s;
  }

  private static float[] scores(IndexSearcher searcher, Query q, int maxDoc) throws IOException {
    float[] sc = new float[maxDoc];
    java.util.Arrays.fill(sc, Float.NaN);
    for (ScoreDoc sd : searcher.search(q, maxDoc).scoreDocs) {
      sc[sd.doc] = sd.score;
    }
    return sc;
  }

  /** Explicit pivot 1000, feature values 1e-5 and 2e-5: S/pivot = 1e-8 and 2e-8. */
  public void testSaturationScoresZeroFarBelowPivot() throws IOException {
    try (Directory dir = newDirectory();
        DirectoryReader reader = index(dir, 1e-5f, 2e-5f)) {
      IndexSearcher searcher = newSearcher(reader);
      float[] s = storedValues(searcher, reader.maxDoc());
      Query q = FeatureField.newSaturationQuery("features", "pop", 1f, 1000f);
      float[] got = scores(searcher, q, reader.maxDoc());
      StringBuilder failures = new StringBuilder();
      for (int doc = 0; doc < reader.maxDoc(); doc++) {
        float expected = documentedSaturation(1, s[doc], 1000);
        if (got[doc] <= 0f || Math.abs(got[doc] - expected) > 2 * Math.ulp(expected)) {
          Explanation e = searcher.explain(q, doc);
          failures.append(
              String.format(
                  java.util.Locale.ROOT,
                  "%n doc %d: S=%s, documented w*S/(S+k) = %s, score = %s; explain: %s = %s",
                  doc,
                  s[doc],
                  expected,
                  got[doc],
                  e.getDescription(),
                  e.getValue()));
        }
      }
      assertTrue(
          "saturation(w=1, pivot=1000) does not score the documented formula:" + failures,
          failures.length() == 0);
    }
  }

  /**
   * Default pivot (computed from index statistics) on a heavy-tailed feature: 4000 values spread
   * evenly in log scale over 1e-6..1e6. Every pair of documents whose documented scores differ
   * (by at least 2 float ulps) must keep their order.
   */
  public void testSaturationDefaultPivotTiesDistinctValues() throws IOException {
    int n = 4000;
    float[] values = new float[n];
    for (int i = 0; i < n; i++) {
      values[i] = (float) Math.pow(10, -6 + 12.0 * i / (n - 1));
    }
    try (Directory dir = newDirectory();
        DirectoryReader reader = index(dir, values)) {
      IndexSearcher searcher = newSearcher(reader);
      float[] s = storedValues(searcher, reader.maxDoc());
      Query q = FeatureField.newSaturationQuery("features", "pop");
      Explanation first = searcher.explain(q, 0);
      float k = first.getDetails()[1].getValue().floatValue(); // "k, pivot feature value ..."
      float[] got = scores(searcher, q, reader.maxDoc());
      Integer[] byValue = new Integer[reader.maxDoc()];
      for (int i = 0; i < byValue.length; i++) byValue[i] = i;
      java.util.Arrays.sort(byValue, (x, y) -> Float.compare(s[x], s[y]));
      int ties = 0, zeros = 0;
      String example = null;
      for (int j = 1; j < byValue.length; j++) {
        int lo = byValue[j - 1], hi = byValue[j];
        float eLo = documentedSaturation(1, s[lo], k), eHi = documentedSaturation(1, s[hi], k);
        boolean distinct = Float.floatToIntBits(eHi) - Float.floatToIntBits(eLo) >= 2;
        if (distinct && got[lo] == got[hi]) {
          ties++;
          if (example == null) {
            example =
                String.format(
                    java.util.Locale.ROOT,
                    "S=%s and S=%s: documented %s < %s, both score %s",
                    s[lo],
                    s[hi],
                    eLo,
                    eHi,
                    got[hi]);
          }
        }
        if (got[hi] == 0f && eHi > 0f) zeros++;
      }
      assertEquals(
          "default pivot k=" + k + ": documents with different documented scores tie, e.g. " + example,
          0,
          ties);
      assertEquals(0, zeros);
    }
  }

  /** Sigmoid, pivot 1, exponent 4, values 5e-5 and 1e-4: documented scores 6.25e-18 and 1e-16. */
  public void testSigmoidScoresZeroBelowPivot() throws IOException {
    try (Directory dir = newDirectory();
        DirectoryReader reader = index(dir, 5e-5f, 1e-4f)) {
      IndexSearcher searcher = newSearcher(reader);
      float[] s = storedValues(searcher, reader.maxDoc());
      Query q = FeatureField.newSigmoidQuery("features", "pop", 1f, 1f, 4f);
      float[] got = scores(searcher, q, reader.maxDoc());
      StringBuilder failures = new StringBuilder();
      for (int doc = 0; doc < reader.maxDoc(); doc++) {
        float expected = documentedSigmoid(1, s[doc], 1, 4);
        if (got[doc] <= 0f || Math.abs(got[doc] - expected) > 2 * Math.ulp(expected)) {
          failures.append(
              String.format(
                  java.util.Locale.ROOT,
                  "%n doc %d: S=%s, documented w*S^a/(S^a+k^a) = %s, score = %s",
                  doc,
                  s[doc],
                  expected,
                  got[doc]));
        }
      }
      assertTrue(
          "sigmoid(w=1, pivot=1, a=4) does not score the documented formula:" + failures,
          failures.length() == 0);
    }
  }
}
