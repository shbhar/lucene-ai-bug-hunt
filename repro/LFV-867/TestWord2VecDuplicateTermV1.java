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
package v1verify;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.synonym.word2vec.Dl4jModelReader;
import org.apache.lucene.analysis.synonym.word2vec.Word2VecModel;
import org.apache.lucene.analysis.synonym.word2vec.Word2VecSynonymFilter;
import org.apache.lucene.analysis.synonym.word2vec.Word2VecSynonymFilterFactory;
import org.apache.lucene.analysis.synonym.word2vec.Word2VecSynonymProvider;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.ResourceLoader;
import org.apache.lucene.util.TermAndVector;
import org.apache.lucene.util.VectorUtil;

/**
 * Independent verifier test (public API only, outside the word2vec package).
 *
 * <p>Claim under test: when a term is added to a Word2VecModel a second time, the vector looked up
 * for every LATER distinct term comes from a different term, so vectorValue(term) and the synonyms
 * of later terms are wrong. Controls use the same entries without the repeated line.
 */
public class TestWord2VecDuplicateTermV1 extends LuceneTestCase {

  // 3-dim vectors; dictionary of colour words. lime ~ green, navy ~ blue, everything else ~orthogonal.
  private static final String[][] ENTRIES_WITH_REPEAT = {
    {"red", "1", "0", "0"},
    {"red", "1", "0", "0"}, // the same term listed a second time (identical vector)
    {"green", "0", "1", "0"},
    {"lime", "0.05", "1", "0"},
    {"blue", "0", "0", "1"},
    {"navy", "0", "0.05", "1"},
  };

  private static final String[][] ENTRIES_NO_REPEAT = {
    {"red", "1", "0", "0"},
    {"green", "0", "1", "0"},
    {"lime", "0.05", "1", "0"},
    {"blue", "0", "0", "1"},
    {"navy", "0", "0.05", "1"},
  };

  private static float[] vec(String[] e) {
    float[] v = new float[e.length - 1];
    for (int i = 1; i < e.length; i++) {
      v[i - 1] = Float.parseFloat(e[i]);
    }
    return v;
  }

  private static float[] unit(float[] v) {
    float[] c = v.clone();
    VectorUtil.l2normalize(c);
    return c;
  }

  private static Word2VecModel buildModel(String[][] entries) {
    Word2VecModel model = new Word2VecModel(entries.length, entries[0].length - 1);
    for (String[] e : entries) {
      model.addTermAndVector(new TermAndVector(new BytesRef(e[0]), vec(e)));
    }
    return model;
  }

  /** Expected vector of a term: the normalized vector of its (first) entry in the model input. */
  private static float[] ownVector(String[][] entries, String term) {
    for (String[] e : entries) {
      if (e[0].equals(term)) {
        return unit(vec(e));
      }
    }
    throw new AssertionError("no entry " + term);
  }

  private static List<String> analyze(TokenStream ts) throws IOException {
    List<String> out = new ArrayList<>();
    CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
    ts.reset();
    while (ts.incrementToken()) {
      out.add(term.toString());
    }
    ts.end();
    ts.close();
    return out;
  }

  private static Tokenizer tokenizer(String text) {
    Tokenizer t = new MockTokenizer(MockTokenizer.WHITESPACE, false);
    t.setReader(new java.io.StringReader(text));
    return t;
  }

  // ---------------------------------------------------------------- direct model lookup

  public void testControlNoRepeatEachTermHasOwnVector() {
    Word2VecModel model = buildModel(ENTRIES_NO_REPEAT);
    for (String t : new String[] {"red", "green", "lime", "blue", "navy"}) {
      assertArrayEquals(
          "vectorValue(" + t + ")",
          ownVector(ENTRIES_NO_REPEAT, t),
          model.vectorValue(new BytesRef(t)),
          1e-6f);
    }
  }

  public void testRepeatedTermLaterTermsKeepOwnVector() {
    Word2VecModel model = buildModel(ENTRIES_WITH_REPEAT);
    StringBuilder wrong = new StringBuilder();
    for (String t : new String[] {"red", "green", "lime", "blue", "navy"}) {
      float[] expected = ownVector(ENTRIES_WITH_REPEAT, t);
      float[] actual = model.vectorValue(new BytesRef(t));
      if (actual == null || !java.util.Arrays.equals(expected, actual)) {
        wrong
            .append(t)
            .append(": expected ")
            .append(java.util.Arrays.toString(expected))
            .append(" but got ")
            .append(java.util.Arrays.toString(actual))
            .append("; ");
      }
    }
    assertEquals("terms whose vectorValue(term) is not their own vector", "", wrong.toString());
  }

  public void testNonAdjacentRepeatShiftsOnlyLaterTerms() {
    // p, q, p (repeat), r: r must still map to r's vector
    String[][] entries = {
      {"pear", "1", "0"}, {"quince", "0", "1"}, {"pear", "1", "0"}, {"rhubarb", "-1", "0"},
    };
    Word2VecModel model = buildModel(entries);
    assertArrayEquals(
        "quince (before the repeat)",
        ownVector(entries, "quince"),
        model.vectorValue(new BytesRef("quince")),
        1e-6f);
    assertArrayEquals(
        "rhubarb (after the repeat) must keep its own vector",
        ownVector(entries, "rhubarb"),
        model.vectorValue(new BytesRef("rhubarb")),
        1e-6f);
  }

  // ---------------------------------------------------------------- through the filter

  public void testControlNoRepeatFilterSynonyms() throws IOException {
    Word2VecSynonymProvider provider = new Word2VecSynonymProvider(buildModel(ENTRIES_NO_REPEAT));
    assertEquals(
        List.of("green", "lime"),
        analyze(new Word2VecSynonymFilter(tokenizer("green"), provider, 5, 0.8f)));
    assertEquals(
        List.of("blue", "navy"),
        analyze(new Word2VecSynonymFilter(tokenizer("blue"), provider, 5, 0.8f)));
  }

  public void testRepeatedTermFilterSynonyms() throws IOException {
    Word2VecSynonymProvider provider =
        new Word2VecSynonymProvider(buildModel(ENTRIES_WITH_REPEAT));
    List<String> green = analyze(new Word2VecSynonymFilter(tokenizer("green"), provider, 5, 0.8f));
    List<String> blue = analyze(new Word2VecSynonymFilter(tokenizer("blue"), provider, 5, 0.8f));
    assertEquals(
        "green -> " + green + ", blue -> " + blue,
        List.of(List.of("green", "lime"), List.of("blue", "navy")),
        List.of(green, blue));
  }

  // ---------------------------------------------------------------- documented file path

  private static byte[] dl4jZip(String[][] entries) throws IOException {
    StringBuilder sb = new StringBuilder();
    sb.append(entries.length).append(' ').append(entries[0].length - 1).append('\n');
    for (String[] e : entries) {
      sb.append(String.join(" ", e)).append('\n');
    }
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(bos)) {
      zos.putNextEntry(new ZipEntry("syn0"));
      zos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();
    }
    return bos.toByteArray();
  }

  private static ResourceLoader loaderFor(String name, byte[] bytes) {
    return new ResourceLoader() {
      @Override
      public InputStream openResource(String resource) throws IOException {
        if (resource.equals(name)) {
          return new ByteArrayInputStream(bytes);
        }
        throw new IOException("no resource " + resource);
      }

      @Override
      public <T> Class<? extends T> findClass(String cname, Class<T> expectedType) {
        throw new UnsupportedOperationException();
      }
    };
  }

  private static List<String> viaFactory(String modelName, String[][] entries, String text)
      throws IOException {
    Map<String, String> args = new HashMap<>();
    args.put("model", modelName);
    Word2VecSynonymFilterFactory factory = new Word2VecSynonymFilterFactory(args);
    factory.inform(loaderFor(modelName, dl4jZip(entries)));
    return analyze(factory.create(tokenizer(text)));
  }

  public void testDl4jReaderRepeatedLineKeepsLaterTermsVectors() throws IOException {
    Word2VecModel model;
    try (Dl4jModelReader reader =
        new Dl4jModelReader(new ByteArrayInputStream(dl4jZip(ENTRIES_WITH_REPEAT)))) {
      model = reader.read();
    }
    assertArrayEquals(
        "Dl4j-read model: vectorValue(green)",
        ownVector(ENTRIES_WITH_REPEAT, "green"),
        model.vectorValue(new BytesRef("green")),
        1e-6f);
  }

  public void testControlFactoryNoRepeat() throws IOException {
    // unique resource names: the provider factory caches providers by resource name
    assertEquals(
        List.of("green", "lime", "blue", "navy"),
        viaFactory("v1-control-model.zip", ENTRIES_NO_REPEAT, "green blue"));
  }

  public void testFactoryRepeatedLine() throws IOException {
    assertEquals(
        List.of("green", "lime", "blue", "navy"),
        viaFactory("v1-repeat-model.zip", ENTRIES_WITH_REPEAT, "green blue"));
  }
}
