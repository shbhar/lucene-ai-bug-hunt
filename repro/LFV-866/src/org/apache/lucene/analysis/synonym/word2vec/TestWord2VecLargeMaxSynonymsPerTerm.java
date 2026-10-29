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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

public class TestWord2VecLargeMaxSynonymsPerTerm extends BaseTokenStreamTestCase {

  public void testMaxIntMaxSynonymsPerTerm() throws Exception {
    Path dir = createTempDir();
    Path model = dir.resolve("max-synonyms-per-term-model.zip");
    try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(model))) {
      zip.putNextEntry(new ZipEntry("syn0.txt"));
      zip.write("2 2\nalpha 1 0\nbeta 0.9 0.1\n".getBytes(StandardCharsets.UTF_8));
    }
    Analyzer a;
    try {
      a =
          CustomAnalyzer.builder(dir)
              .withTokenizer("whitespace")
              .addTokenFilter(
                  Word2VecSynonymFilterFactory.NAME,
                  "model",
                  model.getFileName().toString(),
                  "maxSynonymsPerTerm",
                  Integer.toString(Integer.MAX_VALUE))
              .build();
    } catch (IllegalArgumentException rejectedAtConfiguration) {
      return;
    }
    try {
      assertAnalyzesTo(a, "alpha", new String[] {"alpha", "beta"});
    } catch (IllegalArgumentException e) {
      // expected: alpha plus its synonym beta (cosine 0.99), or a rejection at configuration time
      throw new AssertionError("expected [alpha, beta], got " + e, e);
    }
    a.close();
  }
}
