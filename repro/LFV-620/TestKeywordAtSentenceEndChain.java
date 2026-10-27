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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.miscellaneous.KeywordMarkerFilterFactory;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.util.ClasspathResourceLoader;

/**
 * Independent verifier test: a realistic analysis chain built only from the public factories
 * (OpenNLP tokenizer -> OpenNLP POS -> KeywordMarker -> OpenNLP lemmatizer) using the module's own
 * test models. A token marked as keyword must pass through the lemmatizer unchanged; no token may be
 * dropped, wherever it sits in the sentence.
 */
public class TestKeywordAtSentenceEndChain extends BaseTokenStreamTestCase {

  private static final String TEXT = "They sent him running in the evening";
  private static final String TWO_SENTENCES =
      "They sent him running in the evening. He did not come back.";

  private static Analyzer chain(String keywordPattern, String... lemmatizerArgs)
      throws IOException {
    CustomAnalyzer.Builder b =
        CustomAnalyzer.builder(new ClasspathResourceLoader(TestKeywordAtSentenceEndChain.class))
            .withTokenizer(
                "opennlp",
                "tokenizerModel",
                "en-test-tokenizer.bin",
                "sentenceModel",
                "en-test-sent.bin")
            .addTokenFilter("opennlpPOS", "posTaggerModel", "en-test-pos-maxent.bin");
    if (keywordPattern != null) {
      b.addTokenFilter(KeywordMarkerFilterFactory.class, "pattern", keywordPattern);
    }
    b.addTokenFilter("opennlplemmatizer", lemmatizerArgs);
    return b.build();
  }

  private static List<String> terms(Analyzer a, String text) throws IOException {
    List<String> out = new ArrayList<>();
    try (TokenStream ts = a.tokenStream("f", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        out.add(term.toString());
      }
      ts.end();
    }
    return out;
  }

  private static final String[] DICT = {"dictionary", "en-test-lemmas.dict"};

  /** Control: without keyword marking every token is emitted (passes on all versions). */
  public void testControlNoKeywords() throws Exception {
    try (Analyzer a = chain(null, DICT)) {
      assertEquals(
          Arrays.asList("they", "send", "he", "run", "in", "the", "evening"), terms(a, TEXT));
    }
  }

  /** Control: a keyword in the middle of the sentence is kept unchanged (passes). */
  public void testKeywordMidSentenceKept() throws Exception {
    try (Analyzer a = chain("running", DICT)) {
      assertEquals(
          Arrays.asList("they", "send", "he", "running", "in", "the", "evening"), terms(a, TEXT));
    }
  }

  /** The last token of the text is a keyword: it must still be emitted. */
  public void testKeywordAtEndOfTextKept() throws Exception {
    try (Analyzer a = chain("evening", DICT)) {
      assertEquals(
          Arrays.asList("they", "send", "he", "run", "in", "the", "evening"), terms(a, TEXT));
    }
  }

  /** Same chain, standard BaseTokenStreamTestCase checks (offsets/positions consistency too). */
  public void testKeywordAtEndOfTextKeptAssertAnalyzesTo() throws Exception {
    try (Analyzer a = chain("evening", DICT)) {
      assertAnalyzesTo(a, TEXT, new String[] {"they", "send", "he", "run", "in", "the", "evening"});
    }
  }

  /** Each sentence ends with a keyword token (here: protected punctuation). */
  public void testKeywordEndsEachSentenceKept() throws Exception {
    try (Analyzer a = chain("\\.", DICT)) {
      assertEquals(
          Arrays.asList(
              "they", "send", "he", "run", "in", "the", "evening", ".", "he", "do", "not", "come",
              "back", "."),
          terms(a, TWO_SENTENCES));
    }
  }

  /** A sentence consisting only of keyword tokens must keep all of them. */
  public void testAllKeywordSentenceKept() throws Exception {
    try (Analyzer a = chain(".*", DICT)) {
      assertEquals(
          Arrays.asList("They", "sent", "him", "running", "in", "the", "evening"), terms(a, TEXT));
    }
  }

  /** MaxEnt lemmatizer: the token count must not shrink and the final keyword must survive. */
  public void testKeywordAtEndOfTextKeptMaxEnt() throws Exception {
    List<String> control;
    try (Analyzer a = chain(null, "lemmatizerModel", "en-test-lemmatizer.bin")) {
      control = terms(a, TEXT);
    }
    try (Analyzer a = chain("evening", "lemmatizerModel", "en-test-lemmatizer.bin")) {
      List<String> actual = terms(a, TEXT);
      assertEquals("control=" + control + " actual=" + actual, control.size(), actual.size());
      assertEquals("evening", actual.get(actual.size() - 1));
    }
  }
}
