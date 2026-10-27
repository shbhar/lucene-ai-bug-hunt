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
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * FeatureField.newSigmoidQuery accepts any pivot and exp in (0, +Infinity) and documents the score
 * as weight * S^exp / (S^exp + pivot^exp), a value in [0, weight]. When pivot^exp is out of the
 * range of a double, every score (pivot^exp overflows) or the score of every document whose S^exp
 * also underflows (pivot^exp underflows) is NaN.
 *
 * <p>Uses only public API. All feature values are exactly representable with the 9 significant bits
 * that FeatureField keeps, so the expected scores need no knowledge of the encoding.
 */
public class TestFeatureFieldSigmoidPivotPower extends LuceneTestCase {

  private static final float WEIGHT = 1f;
  private static final float EXP = 60f;

  /** Documented formula, evaluated as w / (1 + (pivot/S)^exp), which has no overflow problem. */
  private static double expectedScore(float s, float pivot) {
    return WEIGHT / (1 + Math.pow((double) pivot / s, EXP));
  }

  private void doTest(float pivot, float[] featureValues, int[] expectedOrder) throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        for (float v : featureValues) {
          Document doc = new Document();
          doc.add(new FeatureField("features", "popularity", v));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        Query q = FeatureField.newSigmoidQuery("features", "popularity", WEIGHT, pivot, EXP);
        TopDocs td = searcher.search(q, 10);
        assertEquals(featureValues.length, td.scoreDocs.length);
        StringBuilder sb = new StringBuilder();
        for (ScoreDoc sd : td.scoreDocs) {
          sb.append(" doc=").append(sd.doc).append(" score=").append(sd.score);
          if (sd.doc >= 0 && sd.doc < featureValues.length) {
            sb.append(" (S=").append(featureValues[sd.doc]);
            sb.append(" expected=").append((float) expectedScore(featureValues[sd.doc], pivot));
            sb.append(')');
          }
        }
        String hits = "pivot=" + pivot + " exp=" + EXP + ":" + sb;
        for (ScoreDoc sd : td.scoreDocs) {
          assertTrue("hit with an invalid doc id; " + hits, sd.doc >= 0 && sd.doc < reader.maxDoc());
        }
        for (ScoreDoc sd : td.scoreDocs) {
          assertFalse("NaN score; " + hits, Float.isNaN(sd.score));
          assertTrue("score outside [0, weight]; " + hits, sd.score >= 0 && sd.score <= WEIGHT);
          assertEquals(hits, expectedScore(featureValues[sd.doc], pivot), sd.score, 1e-6);
          assertEquals(
              "explanation", sd.score, searcher.explain(q, sd.doc).getValue().floatValue(), 0f);
        }
        for (int i = 0; i < expectedOrder.length; i++) {
          assertEquals("rank " + i + "; " + hits, expectedOrder[i], td.scoreDocs[i].doc);
        }
      }
    }
  }

  /** pivot^exp = 2^1200 overflows a double: every document scores NaN. */
  public void testPivotPowerOverflow() throws IOException {
    float pivot = 0x1p20f; // 1048576
    float[] s = {0x1p17f, 0x1.2p20f, 0x1p20f, 0x1.cp19f}; // 131072, 1179648, 1048576, 917504
    doTest(pivot, s, new int[] {1, 2, 3, 0});
  }

  /** pivot^exp = 2^-1200 underflows to 0: documents with S^exp == 0 too score 0/0 = NaN. */
  public void testPivotPowerUnderflow() throws IOException {
    float pivot = 0x1p-20f; // ~9.5e-7
    float[] s = {0x1p-23f, 0x1.2p-20f, 0x1p-20f, 0x1p-17f};
    doTest(pivot, s, new int[] {3, 1, 2, 0});
  }
}
