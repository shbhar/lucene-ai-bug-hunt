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
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Word2VecSynonymFilterFactory accepts any maxSynonymsPerTerm > 0 ("must be a positive integer
 * greater than 0"). Word2VecSynonymProvider.getSynonyms passes maxSynonymsPerTerm + 1 to the HNSW
 * search as the queue size, unclamped by the model size, so a value of 2147483629 or more (e.g.
 * Integer.MAX_VALUE as "no limit, keep everything above the threshold") makes every token that has
 * a model vector throw IllegalArgumentException from the search queue. Public API only.
 */
public class TestWord2VecLargeMaxSynonymsPerTerm extends LuceneTestCase {

  private static List<String> analyze(Analyzer a, String text) throws Exception {
    List<String> out = new ArrayList<>();
    try (TokenStream ts = a.tokenStream("f", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) out.add(term.toString());
      ts.end();
    }
    return out;
  }

  public void testMaxIntMaxSynonymsPerTermAcceptedByFactory() throws Exception {
    Path dir = createTempDir("w2v-large-k");
    String[] words = {"alpha", "beta", "gamma"};
    float[][] vecs = {{1f, 0f}, {0.9f, 0.1f}, {0f, 1f}};
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
    String modelName = "large-k-model-" + random().nextInt(Integer.MAX_VALUE) + ".zip";
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
              .addTokenFilter(
                  Word2VecSynonymFilterFactory.NAME,
                  "model",
                  modelName,
                  "maxSynonymsPerTerm",
                  Integer.toString(Integer.MAX_VALUE))
              .build();
    } catch (IllegalArgumentException rejected) {
      return; // rejecting the value at configuration time would be a valid outcome
    }
    // beta (cosine 0.994 with alpha) is the only term above the default threshold
    assertEquals(List.of("alpha", "beta"), analyze(a, "alpha"));
    a.close();
  }
}
