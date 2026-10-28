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
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.TermAndVector;

/**
 * The Word2VecSynonymFilter constructor documents minAcceptedSimilarity as the "minimal value of
 * cosine similarity between the searched vector and the retrieved ones". Word2VecSynonymProvider
 * compares it with the DOT_PRODUCT score, (1 + cosine) / 2, so a term is accepted when its cosine
 * is at least 2 * minAcceptedSimilarity - 1: the default 0.8 accepts cosine 0.6, 0.5 accepts
 * orthogonal terms and smaller values accept anti-correlated terms. Public API only.
 */
public class TestWord2VecMinAcceptedSimilarityScale extends LuceneTestCase {

  // unit vectors: cosine with "a" is 0.65 for "b", 0 for "c" and -0.5 for "d"
  private static Word2VecSynonymProvider provider() throws Exception {
    Word2VecModel model = new Word2VecModel(4, 2);
    model.addTermAndVector(new TermAndVector(new BytesRef("a"), new float[] {1f, 0f}));
    model.addTermAndVector(
        new TermAndVector(new BytesRef("b"), new float[] {0.65f, (float) Math.sqrt(1 - 0.65 * 0.65)}));
    model.addTermAndVector(new TermAndVector(new BytesRef("c"), new float[] {0f, 1f}));
    model.addTermAndVector(
        new TermAndVector(new BytesRef("d"), new float[] {-0.5f, (float) Math.sqrt(0.75)}));
    return new Word2VecSynonymProvider(model);
  }

  private static List<String> analyze(Word2VecSynonymProvider p, float minAcceptedSimilarity)
      throws Exception {
    Analyzer a =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer t = new WhitespaceTokenizer();
            return new TokenStreamComponents(
                t, new Word2VecSynonymFilter(t, p, 10, minAcceptedSimilarity));
          }
        };
    List<String> out = new ArrayList<>();
    try (TokenStream ts = a.tokenStream("f", "a")) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) out.add(term.toString());
      ts.end();
    }
    a.close();
    return out;
  }

  /** Factory default 0.8: "b" (cosine 0.65) must not be a synonym of "a". */
  public void testDefaultThresholdIsACosine() throws Exception {
    assertEquals(
        "minAcceptedSimilarity="
            + Word2VecSynonymFilterFactory.DEFAULT_MIN_ACCEPTED_SIMILARITY
            + " (cosine of b = 0.65)",
        List.of("a"),
        analyze(provider(), Word2VecSynonymFilterFactory.DEFAULT_MIN_ACCEPTED_SIMILARITY));
  }

  /** 0.5: the orthogonal term "c" (cosine 0) must not be a synonym. */
  public void testOrthogonalTermRejectedAtOneHalf() throws Exception {
    assertEquals(
        "minAcceptedSimilarity=0.5 (cosine of b = 0.65, of c = 0)",
        List.of("a", "b"),
        analyze(provider(), 0.5f));
  }

  /** 0.2: the anti-correlated term "d" (cosine -0.5) must not be a synonym. */
  public void testAntiCorrelatedTermRejected() throws Exception {
    assertEquals(
        "minAcceptedSimilarity=0.2 (cosine of b = 0.65, of c = 0, of d = -0.5)",
        List.of("a", "b"),
        analyze(provider(), 0.2f));
  }
}
