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
package org.apache.lucene.analysis.v3verify;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * Independent check (verifier, intended-behaviour lens): real files on disk, written as raw
 * bytes (EF BB BF + text), loaded through the documented public path CustomAnalyzer.builder(Path),
 * i.e. a filesystem resource loader, with the default synonym format and tokenizer. Public API only.
 */
public class TestSynonymBomOnDisk extends BaseTokenStreamTestCase {

  private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

  private static void writeFile(Path dir, String name, boolean bom, String body) throws Exception {
    byte[] content = body.getBytes(StandardCharsets.UTF_8);
    byte[] all = new byte[(bom ? 3 : 0) + content.length];
    if (bom) System.arraycopy(BOM, 0, all, 0, 3);
    System.arraycopy(content, 0, all, bom ? 3 : 0, content.length);
    Files.write(dir.resolve(name), all);
  }

  private static Analyzer synonyms(Path dir) throws Exception {
    return CustomAnalyzer.builder(dir)
        .withTokenizer("whitespace")
        .addTokenFilter("synonymGraph", "synonyms", "synonyms.txt")
        .build();
  }

  /** Control 1: the same rule file without a BOM: 'foo' gets its synonym. */
  public void testControlNoBom() throws Exception {
    Path dir = createTempDir("v3");
    writeFile(dir, "synonyms.txt", false, "foo, bar\nbaz, qux\n");
    try (Analyzer a = synonyms(dir)) {
      assertAnalyzesTo(a, "foo", new String[] {"bar", "foo"}, new int[] {1, 0});
    }
  }

  /** Control 2: a words file with a BOM, same directory layout, StopFilterFactory: BOM skipped. */
  public void testControlStopWordsWithBom() throws Exception {
    Path dir = createTempDir("v3");
    writeFile(dir, "stopwords.txt", true, "foo\nbar\n");
    try (Analyzer a =
        CustomAnalyzer.builder(dir)
            .withTokenizer("whitespace")
            .addTokenFilter("stop", "words", "stopwords.txt")
            .build()) {
      assertAnalyzesTo(a, "foo baz", new String[] {"baz"});
    }
  }

  /**
   * Observation (passes on main): a BOM before a plain comment first line, the shape of the
   * example synonym files, loses nothing.
   */
  public void testObservationBomBeforePlainComment() throws Exception {
    Path dir = createTempDir("v3");
    writeFile(dir, "synonyms.txt", true, "# my synonyms\nfoo, bar\n");
    try (Analyzer a = synonyms(dir)) {
      assertAnalyzesTo(a, "foo", new String[] {"bar", "foo"}, new int[] {1, 0});
    }
  }

  /** Subject 1: the file of control 1 with a BOM: the first rule should still apply. */
  public void testBomFirstRule() throws Exception {
    Path dir = createTempDir("v3");
    writeFile(dir, "synonyms.txt", true, "foo, bar\nbaz, qux\n");
    try (Analyzer a = synonyms(dir)) {
      assertAnalyzesTo(a, "foo", new String[] {"bar", "foo"}, new int[] {1, 0});
    }
  }

  /** Subject 2: with a BOM, a commented-out first rule should stay a comment. */
  public void testBomCommentedOutFirstRule() throws Exception {
    Path dir = createTempDir("v3");
    writeFile(dir, "synonyms.txt", true, "#couch, sofa\nfoo, bar\n");
    try (Analyzer a = synonyms(dir)) {
      assertAnalyzesTo(a, "sofa", new String[] {"sofa"});
    }
  }

  /**
   * Subject 3: the license header that starts the example synonyms files shipped with Lucene's
   * tests and with the downstream default configuration ("# The ASF licenses this file to You
   * under the Apache License, Version 2.0" holds a comma). With a BOM the header line is no
   * longer a comment and becomes an equivalence rule, so the text "Version 2.0" gains synonyms.
   */
  public void testBomBeforeLicenseHeaderWithComma() throws Exception {
    String header =
        "# The ASF licenses this file to You under the Apache License, Version 2.0\n"
            + "# (the \"License\"); you may not use this file except in compliance with\n"
            + "\n"
            + "foo, bar\n";
    Path dir = createTempDir("v3");
    writeFile(dir, "synonyms.txt", false, header);
    try (Analyzer a = synonyms(dir)) {
      assertAnalyzesTo(a, "Version 2.0", new String[] {"Version", "2.0"});
    }
    Path dirBom = createTempDir("v3");
    writeFile(dirBom, "synonyms.txt", true, header);
    try (Analyzer a = synonyms(dirBom)) {
      assertAnalyzesTo(a, "Version 2.0", new String[] {"Version", "2.0"});
    }
  }
}
