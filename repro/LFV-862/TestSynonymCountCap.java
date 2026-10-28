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
package org.apache.lucene.analysis.synonym.word2vec;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.TermAndVector;

/**
 * Independent check of the documented cap: Word2VecSynonymFilter's maxSynonymsPerTerm is the
 * "maximum number of result returned by the synonym search". Public API only.
 */
public class TestSynonymCountCap extends LuceneTestCase {

  /** Three distinct terms whose vectors are identical; every term is asked for 1 synonym. */
  public void testIdenticalVectorsProvider() throws IOException {
    Word2VecModel model = new Word2VecModel(3, 2);
    model.addTermAndVector(new TermAndVector(new BytesRef("a"), new float[] {1f, 2f}));
    model.addTermAndVector(new TermAndVector(new BytesRef("b"), new float[] {1f, 2f}));
    model.addTermAndVector(new TermAndVector(new BytesRef("c"), new float[] {1f, 2f}));
    Word2VecSynonymProvider provider = new Word2VecSynonymProvider(model);
    List<String> violations = new ArrayList<>();
    for (String t : new String[] {"a", "b", "c"}) {
      Collection<TermAndBoost> syns = provider.getSynonyms(new BytesRef(t), 1, 0.5f);
      if (syns.size() > 1) {
        violations.add(t + " -> " + syns.size() + " synonyms " + syns);
      }
    }
    assertTrue("maxSynonymsPerTerm=1 exceeded: " + violations, violations.isEmpty());
  }

  /** Same model through the token filter: no input token may get more than 1 SYNONYM token. */
  public void testIdenticalVectorsFilter() throws IOException {
    Word2VecModel model = new Word2VecModel(3, 2);
    model.addTermAndVector(new TermAndVector(new BytesRef("a"), new float[] {1f, 2f}));
    model.addTermAndVector(new TermAndVector(new BytesRef("b"), new float[] {1f, 2f}));
    model.addTermAndVector(new TermAndVector(new BytesRef("c"), new float[] {1f, 2f}));
    Word2VecSynonymProvider provider = new Word2VecSynonymProvider(model);
    List<String> violations = new ArrayList<>();
    for (String t : new String[] {"a", "b", "c"}) {
      int n = countSynonymTokens(provider, t, 1, 0.5f);
      if (n > 1) {
        violations.add(t + " -> " + n + " SYNONYM tokens");
      }
    }
    assertTrue("filter maxSynonymsPerTerm=1 exceeded: " + violations, violations.isEmpty());
  }

  /**
   * Pairwise-distinct vectors (no ties), factory defaults maxSynonymsPerTerm=5,
   * minAcceptedSimilarity=0.8. For every term that gets more than 5 synonyms, the test also checks
   * by brute force that the term is its own strict, unique nearest neighbour (so an exact top-6
   * contains it and the exact answer has at most 5 synonyms).
   */
  public void testDistinctVectorsFactoryDefaults() throws IOException {
    checkDistinctModel(5, 0.8f);
  }

  /** Same model, maxSynonymsPerTerm=1 (search size 2). */
  public void testDistinctVectorsMaxOne() throws IOException {
    checkDistinctModel(1, 0.8f);
  }

  private void checkDistinctModel(int k, float minSim) throws IOException {
    final int n = 4000, dim = 32, clusters = 40;
    final double sigma = 0.3;
    Random r = new Random(4000350L); // fixed, independent of tests.seed
    float[][] centers = new float[clusters][dim];
    for (float[] c : centers) {
      for (int d = 0; d < dim; d++) c[d] = (float) r.nextGaussian();
    }
    Word2VecModel model = new Word2VecModel(n, dim);
    for (int i = 0; i < n; i++) {
      float[] c = centers[r.nextInt(clusters)];
      float[] v = new float[dim];
      for (int d = 0; d < dim; d++) v[d] = c[d] + (float) (sigma * r.nextGaussian());
      model.addTermAndVector(new TermAndVector(new BytesRef("w" + i), v));
    }
    float[][] norm = new float[n][];
    for (int i = 0; i < n; i++) norm[i] = model.vectorValue(new BytesRef("w" + i)).clone();

    Word2VecSynonymProvider provider = new Word2VecSynonymProvider(model);
    List<String> violations = new ArrayList<>();
    String firstViolator = null;
    for (int i = 0; i < n; i++) {
      Collection<TermAndBoost> syns = provider.getSynonyms(new BytesRef("w" + i), k, minSim);
      if (syns.size() > k) {
        // brute-force reference: the term must be its own strict unique nearest neighbour
        double self = dot(norm[i], norm[i]);
        double bestOther = Double.NEGATIVE_INFINITY;
        int ties = 0;
        for (int j = 0; j < n; j++) {
          if (j == i) continue;
          double s = dot(norm[i], norm[j]);
          bestOther = Math.max(bestOther, s);
          if (s >= self) ties++;
        }
        assertEquals("w" + i + " has a tie or closer vector; not a distinct-vector case", 0, ties);
        assertTrue(
            "w" + i + " margin too small: self=" + self + " bestOther=" + bestOther,
            self - bestOther > 1e-4);
        violations.add(
            "w"
                + i
                + " -> "
                + syns.size()
                + " (self dot="
                + (float) self
                + ", best other dot="
                + (float) bestOther
                + ")");
        if (firstViolator == null) firstViolator = "w" + i;
      }
    }
    String filterNote = "";
    if (firstViolator != null) {
      filterNote =
          "; filter emits "
              + countSynonymTokens(provider, firstViolator, k, minSim)
              + " SYNONYM tokens for "
              + firstViolator;
    }
    assertTrue(
        "maxSynonymsPerTerm="
            + k
            + ", minAcceptedSimilarity="
            + minSim
            + ": "
            + violations.size()
            + " of "
            + n
            + " terms exceed the cap: "
            + violations.subList(0, Math.min(5, violations.size()))
            + filterNote,
        violations.isEmpty());
  }

  private static double dot(float[] a, float[] b) {
    double s = 0;
    for (int i = 0; i < a.length; i++) s += (double) a[i] * b[i];
    return s;
  }

  private static int countSynonymTokens(
      Word2VecSynonymProvider provider, String text, int k, float minSim) throws IOException {
    Tokenizer tok = new WhitespaceTokenizer();
    tok.setReader(new StringReader(text));
    int count = 0;
    try (TokenStream ts = new Word2VecSynonymFilter(tok, provider, k, minSim)) {
      TypeAttribute type = ts.addAttribute(TypeAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        if (SynonymGraphFilter.TYPE_SYNONYM.equals(type.type())) count++;
      }
      ts.end();
    }
    return count;
  }
}
