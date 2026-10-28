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

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.TermAndVector;
import org.apache.lucene.util.VectorUtil;

/**
 * Word2VecModel.addTermAndVector stores each entry at the next array slot, but vectorValue(BytesRef)
 * looks the term up by its BytesRefHash id. When a term is added twice, BytesRefHash returns the
 * existing id for the repeat and does not advance, so from then on every later term's id points at
 * the slot BEFORE its own: vectorValue(term) silently returns the previous entry's vector, and the
 * synonyms of every later term are computed from another term's vector. Public API only.
 */
public class TestWord2VecModelRepeatedTerm extends BaseTokenStreamTestCase {

  static List<String> tokens(Analyzer a, String text) throws Exception {
    List<String> out = new ArrayList<>();
    try (TokenStream ts = a.tokenStream("f", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      TypeAttribute type = ts.addAttribute(TypeAttribute.class);
      PositionIncrementAttribute posInc = ts.addAttribute(PositionIncrementAttribute.class);
      OffsetAttribute off = ts.addAttribute(OffsetAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        out.add(
            term + "/" + type.type() + "/" + posInc.getPositionIncrement() + "/"
                + off.startOffset() + "-" + off.endOffset());
      }
      ts.end();
    }
    return out;
  }

  private static float[] normalized(float... v) {
    return VectorUtil.l2normalize(v.clone());
  }

  /** Each term must keep its own vector; rejecting the repeated term would also be acceptable. */
  public void testRepeatedTermKeepsLaterTermsVectors() {
    Word2VecModel model = new Word2VecModel(4, 2);
    try {
      model.addTermAndVector(new TermAndVector(new BytesRef("a"), new float[] {1, 0}));
      model.addTermAndVector(new TermAndVector(new BytesRef("a"), new float[] {1, 0}));
      model.addTermAndVector(new TermAndVector(new BytesRef("b"), new float[] {0, 1}));
      model.addTermAndVector(new TermAndVector(new BytesRef("c"), new float[] {-1, 0}));
    } catch (IllegalArgumentException rejected) {
      return; // refusing a repeated term is a valid outcome
    }
    assertArrayEquals(
        "vectorValue(\"b\") must be b's own vector", normalized(0, 1), model.vectorValue(newBytesRef("b")), 0f);
    assertArrayEquals(
        "vectorValue(\"c\") must be c's own vector", normalized(-1, 0), model.vectorValue(newBytesRef("c")), 0f);
  }

  /**
   * End to end through Word2VecSynonymFilter with the factory defaults (5 synonyms, 0.8): "banana"
   * must expand to "fruit" (cosine 0.995), but is expanded with "auto" and "car" (cosine 0.2 and 0).
   */
  public void testRepeatedTermGivesUnrelatedSynonymsThroughFilter() throws Exception {
    Word2VecModel model = new Word2VecModel(5, 2);
    try {
      // e.g. "Car" and "car" of a cased model, both lower-cased while loading
      model.addTermAndVector(new TermAndVector(new BytesRef("car"), new float[] {1f, 0f}));
      model.addTermAndVector(new TermAndVector(new BytesRef("car"), new float[] {1f, 0f}));
      model.addTermAndVector(new TermAndVector(new BytesRef("auto"), new float[] {0.98f, 0.2f}));
      model.addTermAndVector(new TermAndVector(new BytesRef("banana"), new float[] {0f, 1f}));
      model.addTermAndVector(new TermAndVector(new BytesRef("fruit"), new float[] {0.1f, 1f}));
    } catch (IllegalArgumentException rejected) {
      return; // refusing a repeated term is a valid outcome
    }
    Word2VecSynonymProvider provider = new Word2VecSynonymProvider(model);
    Analyzer a =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer t = new WhitespaceTokenizer();
            return new TokenStreamComponents(
                t,
                new Word2VecSynonymFilter(
                    t,
                    provider,
                    Word2VecSynonymFilterFactory.DEFAULT_MAX_SYNONYMS_PER_TERM,
                    Word2VecSynonymFilterFactory.DEFAULT_MIN_ACCEPTED_SIMILARITY));
          }
        };
    assertEquals(
        "tokens for \"banana\" (term/type/posInc/startOffset-endOffset)",
        List.of("banana/word/1/0-6", "fruit/SYNONYM/0/0-6"),
        tokens(a, "banana"));
    a.close();
  }

  /**
   * Same defect through the documented loading path (Dl4j zip model, Word2VecSynonymFilterFactory):
   * a model file that lists "car" twice is accepted silently and "banana" gets car's neighbours.
   */
  public void testRepeatedTermInModelFileThroughFactory() throws Exception {
    Path dir = createTempDir("w2v-repeated-term");
    String[] words = {"car", "car", "auto", "banana", "fruit"};
    float[][] vecs = {{1f, 0f}, {1f, 0f}, {0.98f, 0.2f}, {0f, 1f}, {0.1f, 1f}};
    StringBuilder syn0 = new StringBuilder(words.length + " 2 " + words.length + "\n");
    for (int i = 0; i < words.length; i++) {
      syn0.append("B64:")
          .append(Base64.getEncoder().encodeToString(words[i].getBytes(StandardCharsets.UTF_8)))
          .append(' ')
          .append(vecs[i][0])
          .append(' ')
          .append(vecs[i][1])
          .append('\n');
    }
    // unique file name: Word2VecSynonymProviderFactory caches providers JVM-wide by file name
    String modelName = "repeated-term-model-" + random().nextInt(Integer.MAX_VALUE) + ".zip";
    try (OutputStream os = Files.newOutputStream(dir.resolve(modelName));
        ZipOutputStream zip = new ZipOutputStream(os)) {
      zip.putNextEntry(new ZipEntry("syn0.txt"));
      zip.write(syn0.toString().getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
    Analyzer a;
    try {
      a =
          CustomAnalyzer.builder(dir)
              .withTokenizer("whitespace")
              .addTokenFilter(Word2VecSynonymFilterFactory.NAME, "model", modelName)
              .build();
    } catch (IllegalArgumentException rejected) {
      return; // refusing a repeated term is a valid outcome
    }
    assertEquals(
        "tokens for \"banana\" (term/type/posInc/startOffset-endOffset)",
        List.of("banana/word/1/0-6", "fruit/SYNONYM/0/0-6"),
        tokens(a, "banana"));
    a.close();
  }
}
