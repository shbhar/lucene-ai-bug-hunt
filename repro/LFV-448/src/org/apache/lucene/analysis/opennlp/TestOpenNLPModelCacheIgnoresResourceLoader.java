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
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import opennlp.tools.tokenize.TokenSample;
import opennlp.tools.tokenize.TokenizerFactory;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.CollectionObjectStream;
import opennlp.tools.util.TrainingParameters;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * The OpenNLP factories resolve their model file names through a JVM-wide cache keyed only by the
 * file name, so a factory informed with a different ResourceLoader (another configuration
 * directory) uses a model it never read. (That a changed file is not re-read is the documented
 * design of the cache, see LUCENE-2899, and is not asserted here.)
 *
 * <p>Two valid tokenizer models are used: Lucene's test model (splits "." off words) and a tiny
 * model trained here that keeps "." attached to the word.
 */
public class TestOpenNLPModelCacheIgnoresResourceLoader extends BaseTokenStreamTestCase {

  private static final String TEXT = "Hello world. This is a test.";
  private static final List<String> SPLITS_PERIOD =
      List.of("Hello", "world", ".", "This", "is", "a", "test", ".");
  private static final List<String> KEEPS_PERIOD =
      List.of("Hello", "world.", "This", "is", "a", "test.");

  private static void copyModel(String resource, Path target) throws IOException {
    try (InputStream in =
        TestOpenNLPModelCacheIgnoresResourceLoader.class.getResourceAsStream(resource)) {
      assertNotNull("test model " + resource + " on the classpath", in);
      Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  /** A valid tokenizer model that does not split "." (or ",") off the preceding word. */
  private static void writeKeepsPeriodModel(Path target) throws IOException {
    List<TokenSample> samples = new ArrayList<>();
    String[] lines = {
      "Hello world.", "This is a test.", "Good morning, everyone.", "It works.",
      "One, two, three.", "A test case.", "well<SPLIT>-<SPLIT>known", "state<SPLIT>-<SPLIT>of art."
    };
    for (int i = 0; i < 20; i++) {
      for (String l : lines) samples.add(TokenSample.parse(l, "<SPLIT>"));
    }
    TrainingParameters p = TrainingParameters.defaultParams();
    p.put(TrainingParameters.CUTOFF_PARAM, "0");
    p.put(TrainingParameters.ITERATIONS_PARAM, "20");
    TokenizerModel m =
        TokenizerME.train(
            new CollectionObjectStream<>(samples), new TokenizerFactory("en", null, false, null), p);
    try (OutputStream os = Files.newOutputStream(target)) {
      m.serialize(os);
    }
  }

  private static List<String> terms(Analyzer a) throws IOException {
    List<String> terms = new ArrayList<>();
    try (TokenStream ts = a.tokenStream("f", TEXT)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) terms.add(term.toString());
      ts.end();
    }
    return terms;
  }

  private static List<String> analyze(Path configDir, String sentenceModel, String tokenizerModel)
      throws IOException {
    try (Analyzer a =
        CustomAnalyzer.builder(configDir)
            .withTokenizer(
                "openNlp", "sentenceModel", sentenceModel, "tokenizerModel", tokenizerModel)
            .build()) {
      return terms(a);
    }
  }

  /** Two configuration directories, each with valid models under the same file names. */
  public void testOtherConfigDirectoryUsesItsOwnModel() throws IOException {
    Path dirA = createTempDir("configA");
    copyModel("en-test-sent.bin", dirA.resolve("sentence-model.bin"));
    copyModel("en-test-tokenizer.bin", dirA.resolve("tokenizer-model.bin"));
    Path dirB = createTempDir("configB");
    copyModel("en-test-sent.bin", dirB.resolve("sentence-model.bin"));
    writeKeepsPeriodModel(dirB.resolve("tokenizer-model.bin"));
    // control: dirB's model under a name nobody loaded before keeps the period attached
    Files.copy(dirB.resolve("tokenizer-model.bin"), dirB.resolve("b-tokenizer-model.bin"));
    assertEquals(KEEPS_PERIOD, analyze(dirB, "sentence-model.bin", "b-tokenizer-model.bin"));

    assertEquals(SPLITS_PERIOD, analyze(dirA, "sentence-model.bin", "tokenizer-model.bin"));
    assertEquals(
        "analyzer built from configB must use configB's tokenizer model",
        KEEPS_PERIOD,
        analyze(dirB, "sentence-model.bin", "tokenizer-model.bin"));
  }
}
