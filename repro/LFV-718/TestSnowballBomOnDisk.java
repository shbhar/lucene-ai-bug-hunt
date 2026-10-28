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
package org.apache.lucene.analysis.core;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * Independent check (verifier): a real words file on disk, written as raw bytes EF BB BF + a
 * one-word-per-line list, loaded through the documented public path CustomAnalyzer.builder(Path)
 * (a filesystem resource loader). A one-word-per-line file is valid in both documented formats.
 */
public class TestSnowballBomOnDisk extends BaseTokenStreamTestCase {

  private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

  private static Path writeFile(Path dir, String name, boolean bom, String body) throws Exception {
    byte[] content = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    byte[] all = new byte[(bom ? 3 : 0) + content.length];
    if (bom) System.arraycopy(BOM, 0, all, 0, 3);
    System.arraycopy(content, 0, all, bom ? 3 : 0, content.length);
    return Files.write(dir.resolve(name), all);
  }

  private static Analyzer stop(Path dir, String format) throws Exception {
    return CustomAnalyzer.builder(dir)
        .withTokenizer("whitespace")
        .addTokenFilter("stop", "words", "stopwords.txt", "format", format)
        .build();
  }

  /** Control 1: wordset format, BOM file: 'the' is removed. */
  public void testControlWordsetWithBom() throws Exception {
    Path dir = createTempDir("v3");
    writeFile(dir, "stopwords.txt", true, "the\na\nan\n");
    try (Analyzer a = stop(dir, "wordset")) {
      assertAnalyzesTo(a, "the cat", new String[] {"cat"});
    }
  }

  /** Control 2: snowball format, same file WITHOUT a BOM: 'the' is removed. */
  public void testControlSnowballNoBom() throws Exception {
    Path dir = createTempDir("v3");
    writeFile(dir, "stopwords.txt", false, "the\na\nan\n");
    try (Analyzer a = stop(dir, "snowball")) {
      assertAnalyzesTo(a, "the cat", new String[] {"cat"});
    }
  }

  /** Subject: snowball format, the same BOM file as control 1. */
  public void testSnowballWithBom() throws Exception {
    Path dir = createTempDir("v3");
    writeFile(dir, "stopwords.txt", true, "the\na\nan\n");
    try (Analyzer a = stop(dir, "snowball")) {
      assertAnalyzesTo(a, "the cat", new String[] {"cat"});
    }
  }

  /** Subject: CommonGramsFilterFactory, snowball format, BOM file. */
  public void testCommonGramsSnowballWithBom() throws Exception {
    Path dir = createTempDir("v3");
    writeFile(dir, "common.txt", true, "the\n");
    try (Analyzer a =
        CustomAnalyzer.builder(dir)
            .withTokenizer("whitespace")
            .addTokenFilter("commonGrams", "words", "common.txt", "format", "snowball")
            .build()) {
      assertAnalyzesTo(a, "the cat", new String[] {"the", "the_cat", "cat"});
    }
  }

  /** Control 3: CommonGramsFilterFactory, wordset format, same BOM file. */
  public void testControlCommonGramsWordsetWithBom() throws Exception {
    Path dir = createTempDir("v3");
    writeFile(dir, "common.txt", true, "the\n");
    try (Analyzer a =
        CustomAnalyzer.builder(dir)
            .withTokenizer("whitespace")
            .addTokenFilter("commonGrams", "words", "common.txt", "format", "wordset")
            .build()) {
      assertAnalyzesTo(a, "the cat", new String[] {"the", "the_cat", "cat"});
    }
  }
}
