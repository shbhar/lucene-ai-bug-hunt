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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import opennlp.tools.lemmatizer.DictionaryLemmatizer;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.miscellaneous.KeywordMarkerFilterFactory;
import org.apache.lucene.analysis.opennlp.tools.NLPLemmatizerOp;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.KeywordAttribute;
import org.apache.lucene.analysis.tokenattributes.SentenceAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.util.ClasspathResourceLoader;

/**
 * OpenNLPLemmatizerFilter must pass keyword tokens through unchanged, wherever they sit in a
 * sentence. The first four tests need no model: a fixed token stream supplies the term, the POS
 * type, the sentence index and the keyword flag. The last two use the module's test models.
 */
public class TestOpenNLPLemmatizerFilterKeywords extends BaseTokenStreamTestCase {

  /** In-memory lemma dictionary: word TAB pos TAB lemma. */
  private static final String DICT =
      "they\tNNP\tthey\nsent\tVBD\tsend\nhe\tPRP\the\nran\tVBD\trun\n";

  /** Emits fixed tokens; each is {term, POS type, sentence index, "k" (keyword) or "-"}. */
  private static final class SentenceTokens extends TokenStream {
    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private final TypeAttribute typeAtt = addAttribute(TypeAttribute.class);
    private final SentenceAttribute sentenceAtt = addAttribute(SentenceAttribute.class);
    private final KeywordAttribute keywordAtt = addAttribute(KeywordAttribute.class);
    private final String[][] tokens;
    private int upto;

    SentenceTokens(String[][] tokens) {
      this.tokens = tokens;
    }

    @Override
    public boolean incrementToken() {
      clearAttributes();
      if (upto == tokens.length) {
        return false;
      }
      String[] t = tokens[upto++];
      termAtt.setEmpty().append(t[0]);
      typeAtt.setType(t[1]);
      sentenceAtt.setSentenceIndex(Integer.parseInt(t[2]));
      keywordAtt.setKeyword("k".equals(t[3]));
      return true;
    }

    @Override
    public void reset() throws IOException {
      super.reset();
      upto = 0;
    }
  }

  private static TokenStream lemmatize(String[]... tokens) throws IOException {
    DictionaryLemmatizer dictionary =
        new DictionaryLemmatizer(new ByteArrayInputStream(DICT.getBytes(StandardCharsets.UTF_8)));
    return new OpenNLPLemmatizerFilter(
        new SentenceTokens(tokens), new NLPLemmatizerOp(dictionary, null));
  }

  /** Control: a keyword in the middle of a sentence is kept. */
  public void testKeywordInsideSentence() throws Exception {
    assertTokenStreamContents(
        lemmatize(
            new String[] {"they", "NNP", "0", "-"},
            new String[] {"Acme", "NNP", "0", "k"},
            new String[] {"sent", "VBD", "0", "-"},
            new String[] {"he", "PRP", "1", "-"},
            new String[] {"ran", "VBD", "1", "-"}),
        new String[] {"they", "Acme", "send", "he", "run"});
  }

  /** A keyword ends the first sentence. */
  public void testKeywordEndsSentence() throws Exception {
    assertTokenStreamContents(
        lemmatize(
            new String[] {"they", "NNP", "0", "-"},
            new String[] {"sent", "VBD", "0", "-"},
            new String[] {"Acme", "NNP", "0", "k"},
            new String[] {"he", "PRP", "1", "-"},
            new String[] {"ran", "VBD", "1", "-"}),
        new String[] {"they", "send", "Acme", "he", "run"});
  }

  /** A keyword is the last token of the stream. */
  public void testKeywordEndsStream() throws Exception {
    assertTokenStreamContents(
        lemmatize(
            new String[] {"they", "NNP", "0", "-"},
            new String[] {"sent", "VBD", "0", "-"},
            new String[] {"he", "PRP", "1", "-"},
            new String[] {"ran", "VBD", "1", "-"},
            new String[] {"Acme", "NNP", "1", "k"}),
        new String[] {"they", "send", "he", "run", "Acme"});
  }

  /** Every token of the first sentence is a keyword. */
  public void testAllKeywordSentence() throws Exception {
    assertTokenStreamContents(
        lemmatize(
            new String[] {"Acme", "NNP", "0", "k"},
            new String[] {"Widget", "NNP", "0", "k"},
            new String[] {"he", "PRP", "1", "-"},
            new String[] {"ran", "VBD", "1", "-"}),
        new String[] {"Acme", "Widget", "he", "run"});
  }

  private Analyzer chain(String protectedPattern) throws IOException {
    return CustomAnalyzer.builder(new ClasspathResourceLoader(getClass()))
        .withTokenizer(
            "opennlp",
            "tokenizerModel",
            "en-test-tokenizer.bin",
            "sentenceModel",
            "en-test-sent.bin")
        .addTokenFilter("opennlpPOS", "posTaggerModel", "en-test-pos-maxent.bin")
        .addTokenFilter(KeywordMarkerFilterFactory.class, "pattern", protectedPattern)
        .addTokenFilter("opennlplemmatizer", "dictionary", "en-test-lemmas.dict")
        .build();
  }

  /** Control: the same chain with a pattern that matches no word. */
  public void testFactoryChainNothingProtected() throws Exception {
    try (Analyzer a = chain("nomatch")) {
      assertAnalyzesTo(
          a,
          "They sent him running in the evening",
          new String[] {"they", "send", "he", "run", "in", "the", "evening"});
    }
  }

  /** The protected word is the last word of the text, as in a title or a query. */
  public void testFactoryChainProtectedLastWord() throws Exception {
    try (Analyzer a = chain("evening")) {
      assertAnalyzesTo(
          a,
          "They sent him running in the evening",
          new String[] {"they", "send", "he", "run", "in", "the", "evening"});
    }
  }
}
