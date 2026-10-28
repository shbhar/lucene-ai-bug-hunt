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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.util.FilesystemResourceLoader;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.util.ClasspathResourceLoader;

/**
 * The sentence-aware OpenNLP filters (POS, chunker, lemmatizer) read their input one sentence at a
 * time through SentenceAttributeExtractor, which assumes that the first token it sees belongs to
 * sentence 0. When an earlier filter has removed every token of the first sentence, the first
 * token it sees has sentence index 1 or more; the extractor then returns an empty first sentence
 * and the filter ends the stream, so no token of the document is emitted.
 *
 * <p>Uses the test models of lucene/analysis/opennlp/src/test-files.
 */
public class TestSentenceFiltersFirstSentenceRemoved extends BaseTokenStreamTestCase {

  private static final String SENT = "en-test-sent.bin";
  private static final String TOK = "en-test-tokenizer.bin";
  private static final String POS = "en-test-pos-maxent.bin";

  /** Tokenizer, LengthFilter (drops "No" and "."), POS filter. */
  public void testPOSFilter() throws Exception {
    try (Analyzer a =
        CustomAnalyzer.builder(new ClasspathResourceLoader(getClass()))
            .withTokenizer("opennlp", "tokenizerModel", TOK, "sentenceModel", SENT)
            .addTokenFilter("length", "min", "3", "max", "100")
            .addTokenFilter("opennlpPOS", "posTaggerModel", POS)
            .build()) {
      assertAnalyzesTo(
          a, "No. They sent him running.", new String[] {"They", "sent", "him", "running"});
    }
  }

  /** Tokenizer, POS filter, LengthFilter, chunker. */
  public void testChunkerFilter() throws Exception {
    try (Analyzer a =
        CustomAnalyzer.builder(new ClasspathResourceLoader(getClass()))
            .withTokenizer("opennlp", "tokenizerModel", TOK, "sentenceModel", SENT)
            .addTokenFilter("opennlpPOS", "posTaggerModel", POS)
            .addTokenFilter("length", "min", "3", "max", "100")
            .addTokenFilter("opennlpChunker", "chunkerModel", "en-test-chunker.bin")
            .build()) {
      assertAnalyzesTo(
          a, "No. They sent him running.", new String[] {"They", "sent", "him", "running"});
    }
  }

  /**
   * "Keep only nouns, then lemmatize them": tokenizer, POS filter, TypeTokenFilter with a noun
   * allow-list, lemmatizer. The first sentence has no noun, so the type filter removes all of it.
   */
  public void testNounOnlyLemmatizer() throws Exception {
    Path dir = createTempDir("opennlp-config");
    Files.writeString(dir.resolve("keep.pos.txt"), "NN\nNNS\nNNP\nNNPS\n", StandardCharsets.UTF_8);
    try (Analyzer a =
        CustomAnalyzer.builder(
                new FilesystemResourceLoader(dir, new ClasspathResourceLoader(getClass())))
            .withTokenizer("opennlp", "tokenizerModel", TOK, "sentenceModel", SENT)
            .addTokenFilter("opennlpPOS", "posTaggerModel", POS)
            .addTokenFilter("type", "types", "keep.pos.txt", "useWhitelist", "true")
            .addTokenFilter("opennlplemmatizer", "dictionary", "en-test-lemmas.dict")
            .build()) {
      // control: without the noun-free first sentence the nouns come out lemmatized
      assertAnalyzesTo(
          a, "They sent him running in the evening.", new String[] {"they", "evening"});
      // the same sentence after a first sentence without nouns: nothing comes out
      assertAnalyzesTo(
          a,
          "He did not come back. They sent him running in the evening.",
          new String[] {"they", "evening"});
    }
  }
}
