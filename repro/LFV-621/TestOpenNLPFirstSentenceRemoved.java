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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.util.FilesystemResourceLoader;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.util.ClasspathResourceLoader;

/**
 * The OpenNLP POS, chunker and lemmatizer filters tag or rewrite terms; they never remove tokens.
 * When an earlier filter in the chain removes every token of the first sentence, they emit no token
 * at all for the whole value. A removed sentence in the middle is handled correctly.
 *
 * <p>Uses the module's test models (src/test-files).
 */
public class TestOpenNLPFirstSentenceRemoved extends BaseTokenStreamTestCase {

  private static final String SENT = "en-test-sent.bin";
  private static final String TOK = "en-test-tokenizer.bin";
  private static final String POS = "en-test-pos-maxent.bin";
  private static final String CHUNKER = "en-test-chunker.bin";
  private static final String LEMMAS = "en-test-lemmas.dict";

  private static final String REST = "They sent him running in the evening. He did not come back.";
  private static final String[] REST_TERMS = {
    "They", "sent", "him", "running", "in", "the", "evening", "He", "did", "not", "come", "back"
  };

  /** Stop words "hi" and "." (ignoreCase), and a POS allow-list that keeps only nouns. */
  private FilesystemResourceLoader loader() throws IOException {
    Path dir = createTempDir("opennlp-config");
    Files.writeString(dir.resolve("stop.txt"), "hi\n.\n", StandardCharsets.UTF_8);
    Files.writeString(dir.resolve("nouns.txt"), "NN\nNNS\nNNP\nNNPS\n", StandardCharsets.UTF_8);
    return new FilesystemResourceLoader(dir, new ClasspathResourceLoader(getClass()));
  }

  /** "Keep only nouns, then lemmatize them"; the first sentence has no noun. */
  public void testNounsOnlyThenLemmatize() throws Exception {
    try (Analyzer a =
        CustomAnalyzer.builder(loader())
            .withTokenizer("opennlp", "tokenizerModel", TOK, "sentenceModel", SENT)
            .addTokenFilter("opennlpPOS", "posTaggerModel", POS)
            .addTokenFilter("type", "types", "nouns.txt", "useWhitelist", "true")
            .addTokenFilter("opennlplemmatizer", "dictionary", LEMMAS)
            .build()) {
      // passes: the second sentence on its own
      assertAnalyzesTo(
          a, "They sent him running in the evening.", new String[] {"they", "evening"});
      // fails: the same sentence after a first sentence without a noun gives no token at all
      assertAnalyzesTo(
          a,
          "He did not come back. They sent him running in the evening.",
          new String[] {"they", "evening"});
    }
  }

  /** POS filter, then a stop filter that removes "Hi" and ".", then the chunker. */
  public void testChunkerAfterStopFilter() throws Exception {
    try (Analyzer a =
        CustomAnalyzer.builder(loader())
            .withTokenizer("opennlp", "tokenizerModel", TOK, "sentenceModel", SENT)
            .addTokenFilter("opennlpPOS", "posTaggerModel", POS)
            .addTokenFilter("stop", "words", "stop.txt", "ignoreCase", "true")
            .addTokenFilter("opennlpChunker", "chunkerModel", CHUNKER)
            .build()) {
      // passes: no sentence removed
      assertAnalyzesTo(a, REST, REST_TERMS);
      // passes: the removed sentence "Hi." is in the middle
      assertAnalyzesTo(
          a, "They sent him running in the evening. Hi. He did not come back.", REST_TERMS);
      // fails: the removed sentence "Hi." comes first, and no token comes out
      assertAnalyzesTo(a, "Hi. " + REST, REST_TERMS);
    }
  }

  /** A stop filter that removes "Hi" and ".", then the POS filter. */
  public void testPOSFilterAfterStopFilter() throws Exception {
    try (Analyzer a =
        CustomAnalyzer.builder(loader())
            .withTokenizer("opennlp", "tokenizerModel", TOK, "sentenceModel", SENT)
            .addTokenFilter("stop", "words", "stop.txt", "ignoreCase", "true")
            .addTokenFilter("opennlpPOS", "posTaggerModel", POS)
            .build()) {
      assertAnalyzesTo(a, REST, REST_TERMS);
      assertAnalyzesTo(
          a, "They sent him running in the evening. Hi. He did not come back.", REST_TERMS);
      assertAnalyzesTo(a, "Hi. " + REST, REST_TERMS);
    }
  }
}
