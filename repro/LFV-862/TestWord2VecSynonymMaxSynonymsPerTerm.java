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
import java.util.List;
import java.util.Locale;
import java.util.Random;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.TermAndVector;

/**
 * Word2VecSynonymFilter: "@param maxSynonymsPerTerm maximum number of result returned by the
 * synonym search". Word2VecSynonymProvider searches for maxSynonymsPerTerm + 1 neighbours and drops
 * only the query term, so when the query term is not among the hits all of them are returned.
 */
public class TestWord2VecSynonymMaxSynonymsPerTerm extends LuceneTestCase {

  /** The SYNONYM tokens Word2VecSynonymFilter emits for a one-word input. */
  private static List<String> synonymTokens(
      Word2VecSynonymProvider provider, String word, int maxSynonymsPerTerm, float minSimilarity)
      throws IOException {
    Tokenizer tokenizer = new WhitespaceTokenizer();
    tokenizer.setReader(new StringReader(word));
    List<String> out = new ArrayList<>();
    try (TokenStream ts =
        new Word2VecSynonymFilter(tokenizer, provider, maxSynonymsPerTerm, minSimilarity)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      TypeAttribute type = ts.addAttribute(TypeAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        if (SynonymGraphFilter.TYPE_SYNONYM.equals(type.type())) {
          out.add(term.toString());
        }
      }
      ts.end();
    }
    return out;
  }

  /** Three words with the same vector; ask for at most 1 synonym each. */
  public void testTiedVectors() throws IOException {
    Word2VecModel model = new Word2VecModel(3, 2);
    for (String word : new String[] {"a", "b", "c"}) {
      model.addTermAndVector(new TermAndVector(new BytesRef(word), new float[] {1f, 2f}));
    }
    Word2VecSynonymProvider provider = new Word2VecSynonymProvider(model);
    for (String word : new String[] {"a", "b", "c"}) {
      int returned = provider.getSynonyms(new BytesRef(word), 1, 0.5f).size();
      List<String> emitted = synonymTokens(provider, word, 1, 0.5f);
      assertTrue(
          "maxSynonymsPerTerm=1, but getSynonyms("
              + word
              + ") returned "
              + returned
              + " and the filter emitted "
              + emitted,
          returned <= 1 && emitted.size() <= 1);
    }
  }

  /**
   * No ties: 500 distinct vectors in 25 clusters (16 dimensions) and the factory defaults
   * (maxSynonymsPerTerm 5, minAcceptedSimilarity 0.8). Every term is its own strict nearest
   * neighbour, so an exact top-6 would contain it.
   */
  public void testDistinctVectorsFactoryDefaults() throws IOException {
    int n = 500, dim = 16, clusterSize = 20;
    Random random = new Random(3); // fixed model, independent of the test seed
    Word2VecModel model = new Word2VecModel(n, dim);
    float[] center = new float[dim];
    for (int i = 0; i < n; i++) {
      if (i % clusterSize == 0) {
        double norm = 0;
        for (int j = 0; j < dim; j++) {
          center[j] = (float) random.nextGaussian();
          norm += center[j] * center[j];
        }
        for (int j = 0; j < dim; j++) {
          center[j] /= (float) Math.sqrt(norm);
        }
      }
      float[] v = new float[dim];
      for (int j = 0; j < dim; j++) {
        v[j] = (float) (center[j] + 0.35f * random.nextGaussian() / Math.sqrt(dim));
      }
      model.addTermAndVector(new TermAndVector(new BytesRef("w" + i), v));
    }
    Word2VecSynonymProvider provider = new Word2VecSynonymProvider(model);
    int max = Word2VecSynonymFilterFactory.DEFAULT_MAX_SYNONYMS_PER_TERM;
    float min = Word2VecSynonymFilterFactory.DEFAULT_MIN_ACCEPTED_SIMILARITY;
    List<String> violations = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      List<String> emitted = synonymTokens(provider, "w" + i, max, min);
      if (emitted.size() > max) {
        float[] self = model.vectorValue(new BytesRef("w" + i));
        double bestOther = Double.NEGATIVE_INFINITY;
        for (int j = 0; j < n; j++) {
          if (j != i) {
            bestOther = Math.max(bestOther, dot(self, model.vectorValue(new BytesRef("w" + j))));
          }
        }
        violations.add(
            String.format(
                Locale.ROOT,
                "w%d -> %s (cosine with itself %.3f, best other %.3f)",
                i,
                emitted,
                dot(self, self),
                bestOther));
      }
    }
    String message = "maxSynonymsPerTerm=" + max + ", but these terms got more: " + violations;
    assertTrue(message, violations.isEmpty());
  }

  private static double dot(float[] a, float[] b) {
    double sum = 0;
    for (int i = 0; i < a.length; i++) {
      sum += (double) a[i] * b[i];
    }
    return sum;
  }
}
