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
package org.apache.lucene.analysis.opennlp;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.util.ClasspathResourceLoader;

/**
 * OpenNLPLemmatizerFilter must pass keyword tokens through unchanged. A keyword token that ends a
 * sentence is dropped instead, because the filter decides that a sentence is finished once it has
 * emitted as many tokens as it has lemmas (lemmas exist only for non-keyword tokens).
 *
 * <p>Uses the test models of lucene/analysis/opennlp/src/test-files.
 */
public class TestLemmatizerKeepsSentenceFinalKeywords extends BaseTokenStreamTestCase {

  private Analyzer analyzer(String protectedPattern) throws Exception {
    return CustomAnalyzer.builder(new ClasspathResourceLoader(getClass()))
        .withTokenizer(
            "opennlp", "tokenizerModel", "en-test-tokenizer.bin", "sentenceModel", "en-test-sent.bin")
        .addTokenFilter("opennlpPOS", "posTaggerModel", "en-test-pos-maxent.bin")
        .addTokenFilter("keywordMarker", "pattern", protectedPattern)
        .addTokenFilter("opennlplemmatizer", "dictionary", "en-test-lemmas.dict")
        .build();
  }

  /** The protected word is the last word of the text (no terminal punctuation). */
  public void testKeywordAtEndOfText() throws Exception {
    try (Analyzer a = analyzer("evening")) {
      assertAnalyzesTo(
          a,
          "They sent him running in the evening",
          new String[] {"they", "send", "he", "run", "in", "the", "evening"});
    }
  }

  /** A protected word ends the first sentence; the second sentence has no keyword at its end. */
  public void testKeywordEndsInnerSentence() throws Exception {
    try (Analyzer a = analyzer("back|\\.")) {
      // "back" and "." are protected; the first sentence ends with both
      assertAnalyzesTo(
          a,
          "He did not come back. They sent him running in the evening",
          new String[] {
            "he", "do", "not", "come", "back", ".", "they", "send", "he", "run", "in", "the",
            "evening"
          });
    }
  }

  /** Same chain without keyword marking: every token is emitted (control). */
  public void testControlWithoutKeywords() throws Exception {
    try (Analyzer a = analyzer("nomatch")) {
      assertAnalyzesTo(
          a,
          "They sent him running in the evening",
          new String[] {"they", "send", "he", "run", "in", "the", "evening"});
    }
  }
}
