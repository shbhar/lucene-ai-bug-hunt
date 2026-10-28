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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.TermAndVector;

/**
 * Word2VecSynonymFilter documents maxSynonymsPerTerm as the "maximum number of result returned by
 * the synonym search". Word2VecSynonymProvider.getSynonyms searches the HNSW graph for
 * maxSynonymsPerTerm + 1 neighbours, assuming the query term is always among them ("it's always the
 * nearest neighbour of itself"), and only removes the query term. When the query term is not among
 * the hits (another term has the same vector, or the approximate search misses it), all
 * maxSynonymsPerTerm + 1 hits are returned. Public API only.
 */
public class TestWord2VecMaxSynonymsPerTerm extends LuceneTestCase {

  private static List<String> terms(Collection<TermAndBoost> synonyms) {
    List<String> out = new ArrayList<>();
    for (TermAndBoost tb : synonyms) out.add(tb.term().utf8ToString());
    return out;
  }

  /** Four terms with the same vector, maxSynonymsPerTerm = 1. */
  public void testProviderReturnsAtMostMaxSynonyms() throws Exception {
    String[] words = {"x", "y", "z", "w"};
    Word2VecModel model = new Word2VecModel(words.length, 2);
    for (String w : words) {
      model.addTermAndVector(new TermAndVector(new BytesRef(w), new float[] {1f, 0f}));
    }
    Word2VecSynonymProvider provider = new Word2VecSynonymProvider(model);
    for (String w : words) {
      Collection<TermAndBoost> synonyms = provider.getSynonyms(new BytesRef(w), 1, 0.5f);
      assertTrue(
          "maxSynonymsPerTerm=1 but term " + w + " got " + terms(synonyms),
          synonyms.size() <= 1);
    }
  }

  /** The same model through Word2VecSynonymFilter: at most one SYNONYM token per input token. */
  public void testFilterEmitsAtMostMaxSynonymsPerTerm() throws Exception {
    String[] words = {"x", "y", "z", "w"};
    Word2VecModel model = new Word2VecModel(words.length, 2);
    for (String w : words) {
      model.addTermAndVector(new TermAndVector(new BytesRef(w), new float[] {1f, 0f}));
    }
    Word2VecSynonymProvider provider = new Word2VecSynonymProvider(model);
    Analyzer a =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer t = new WhitespaceTokenizer();
            return new TokenStreamComponents(t, new Word2VecSynonymFilter(t, provider, 1, 0.5f));
          }
        };
    for (String w : words) {
      List<String> synonymTokens = new ArrayList<>();
      try (TokenStream ts = a.tokenStream("f", w)) {
        CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
        TypeAttribute type = ts.addAttribute(TypeAttribute.class);
        ts.reset();
        while (ts.incrementToken()) {
          if ("SYNONYM".equals(type.type())) synonymTokens.add(term.toString());
        }
        ts.end();
      }
      assertTrue(
          "maxSynonymsPerTerm=1 but \"" + w + "\" was expanded with " + synonymTokens,
          synonymTokens.size() <= 1);
    }
    a.close();
  }

  /**
   * No tied vectors: 500 distinct terms in 25 clusters of near-synonyms (16 dimensions), with the
   * factory defaults maxSynonymsPerTerm = 5 and minAcceptedSimilarity = 0.8. The approximate graph
   * search with a beam of 6 misses the query term itself for one term, which then gets 6 synonyms.
   */
  public void testDistinctVectorsWithFactoryDefaults() throws Exception {
    int n = 500, dim = 16, clusterSize = 20;
    float noise = 0.35f;
    Random r = new Random(3); // fixed model, independent of the test seed
    Word2VecModel model = new Word2VecModel(n, dim);
    float[] center = new float[dim];
    for (int i = 0; i < n; i++) {
      if (i % clusterSize == 0) {
        double norm = 0;
        for (int j = 0; j < dim; j++) {
          center[j] = (float) r.nextGaussian();
          norm += center[j] * center[j];
        }
        norm = Math.sqrt(norm);
        for (int j = 0; j < dim; j++) center[j] /= norm;
      }
      float[] v = new float[dim];
      for (int j = 0; j < dim; j++) {
        v[j] = (float) (center[j] + noise * r.nextGaussian() / Math.sqrt(dim));
      }
      model.addTermAndVector(new TermAndVector(new BytesRef("w" + i), v));
    }
    Word2VecSynonymProvider provider = new Word2VecSynonymProvider(model);
    int max = Word2VecSynonymFilterFactory.DEFAULT_MAX_SYNONYMS_PER_TERM;
    float min = Word2VecSynonymFilterFactory.DEFAULT_MIN_ACCEPTED_SIMILARITY;
    List<String> violations = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      Collection<TermAndBoost> synonyms = provider.getSynonyms(new BytesRef("w" + i), max, min);
      if (synonyms.size() > max) violations.add("w" + i + " -> " + terms(synonyms));
    }
    assertTrue(
        "maxSynonymsPerTerm=" + max + " exceeded for " + violations.size() + " term(s): " + violations,
        violations.isEmpty());
  }
}
