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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.TermAndVector;

/** A term listed twice in a word2vec model must not change the vectors of the other terms. */
public class TestWord2VecRepeatedTerm extends BaseTokenStreamTestCase {

  public void testRepeatedTermKeepsLaterVectors() {
    Word2VecModel model = new Word2VecModel(4, 2);
    model.addTermAndVector(new TermAndVector(new BytesRef("a"), new float[] {1, 0}));
    try {
      model.addTermAndVector(new TermAndVector(new BytesRef("a"), new float[] {1, 0}));
    } catch (IllegalArgumentException _) {
      return; // rejecting the repeated term is a valid fix
    }
    model.addTermAndVector(new TermAndVector(new BytesRef("b"), new float[] {0, 1}));
    model.addTermAndVector(new TermAndVector(new BytesRef("c"), new float[] {-1, 0}));
    assertArrayEquals("vector of b", new float[] {0, 1}, model.vectorValue(new BytesRef("b")), 0f);
    assertArrayEquals("vector of c", new float[] {-1, 0}, model.vectorValue(new BytesRef("c")), 0f);
  }

  public void testRepeatedTermInModelFile() throws Exception {
    // syn0 file of a Deeplearning4j model zip: "<words> <dimension>", then one word and vector per
    // line; "car" is listed twice
    String syn0 = "5 2\ncar 1 0\ncar 1 0\nauto 0.98 0.2\nbanana 0 1\nfruit 0.1 1\n";
    Path dir = createTempDir();
    // the factory caches models by file name, so use a fresh name
    String model = "repeated-term-" + random().nextInt(Integer.MAX_VALUE) + ".zip";
    try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(dir.resolve(model)))) {
      zip.putNextEntry(new ZipEntry("syn0.txt"));
      zip.write(syn0.getBytes(StandardCharsets.UTF_8));
    }
    Analyzer analyzer;
    try {
      analyzer =
          CustomAnalyzer.builder(dir)
              .withTokenizer("whitespace")
              .addTokenFilter(Word2VecSynonymFilterFactory.NAME, "model", model)
              .build();
    } catch (IllegalArgumentException _) {
      return; // rejecting the repeated term is a valid fix
    }
    // default settings (maxSynonymsPerTerm=5, minAcceptedSimilarity=0.8): the only word near
    // banana (0, 1) is fruit (0.1, 1), cosine 0.995
    assertEquals(List.of("banana", "fruit"), terms(analyzer, "banana"));
    analyzer.close();
  }

  private static List<String> terms(Analyzer analyzer, String text) throws Exception {
    List<String> terms = new ArrayList<>();
    try (TokenStream ts = analyzer.tokenStream("field", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        terms.add(term.toString());
      }
      ts.end();
    }
    return terms;
  }
}
