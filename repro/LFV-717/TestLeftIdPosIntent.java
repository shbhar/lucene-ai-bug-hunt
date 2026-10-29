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
package org.apache.lucene.analysis.ko.dict;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.lucene.analysis.ko.POS;
import org.apache.lucene.analysis.morph.BinaryDictionary;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IntsRef;

/**
 * Intended-behaviour probe. Each test prints what the builder did (rejected, or which left POS each
 * word got) and asserts the "each word keeps its own POS, or the build is rejected" contract.
 */
public class TestLeftIdPosIntent extends LuceneTestCase {

  private static Path file(Path dir, Class<?> clazz, String suffix) {
    return dir.resolve(clazz.getName().replace('.', '/') + suffix);
  }

  private static TokenInfoDictionary build(Path dir, String... rows) throws IOException {
    Files.write(dir.resolve("test.csv"), List.of(rows), StandardCharsets.UTF_8);
    Files.createFile(dir.resolve("unk.def"));
    Files.createFile(dir.resolve("char.def"));
    Files.write(dir.resolve("matrix.def"), List.of("3 3"), StandardCharsets.US_ASCII);
    DictionaryBuilder.build(dir, dir, "utf-8", false);
    return new TokenInfoDictionary(
        file(dir, TokenInfoDictionary.class, BinaryDictionary.TARGETMAP_FILENAME_SUFFIX),
        file(dir, TokenInfoDictionary.class, BinaryDictionary.POSDICT_FILENAME_SUFFIX),
        file(dir, TokenInfoDictionary.class, BinaryDictionary.DICT_FILENAME_SUFFIX),
        file(dir, TokenInfoDictionary.class, TokenInfoDictionary.FST_FILENAME_SUFFIX));
  }

  private static POS.Tag leftPos(TokenInfoDictionary dict, int sourceId) {
    IntsRef ref = new IntsRef();
    dict.lookupWordIds(sourceId, ref);
    return dict.getMorphAttributes().getLeftPOS(ref.ints[ref.offset]);
  }

  private void check(String label, String[] rows, POS.Tag... expected) throws IOException {
    TokenInfoDictionary dict;
    try {
      dict = build(createTempDir(), rows);
    } catch (IllegalArgumentException | IllegalStateException rejected) {
      System.out.println("V3 " + label + ": REJECTED " + rejected);
      return;
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < expected.length; i++) {
      sb.append(' ').append(leftPos(dict, i));
    }
    System.out.println("V3 " + label + ": accepted, left POS =" + sb);
    for (int i = 0; i < expected.length; i++) {
      assertEquals(label + " source id " + i, expected[i], leftPos(dict, i));
    }
  }

  /** Control: distinct left ids, each word keeps its POS. */
  public void testControlDistinctIds() throws Exception {
    check(
        "control-distinct-ids",
        new String[] {"가,1,1,0,NNG,*,F,가,*,*,*,*", "는,2,1,0,JX,*,T,는,*,*,*,*"},
        POS.Tag.NNG,
        POS.Tag.JX);
  }

  /** Same left id, same POS, different semantic class: harmless (semantic class is not stored). */
  public void testSamePosDifferentSemanticClass() throws Exception {
    check(
        "same-pos-diff-semantic",
        new String[] {"가,1,1,0,NNP,지명,F,가,*,*,*,*", "나,1,1,0,NNP,인명,F,나,*,*,*,*"},
        POS.Tag.NNP,
        POS.Tag.NNP);
  }

  /**
   * The mecab-ko-dic wildcard path: Preanalysis rows whose first-POS column is '*' with semantic
   * class 지명 get left id 1 from mecab-ko-dic's own rewrite.def/left-id.def, while nori takes the
   * POS from column 4. Two such rows with different column-4 POS.
   */
  public void testPreanalysisWildcardLeftId() throws Exception {
    check(
        "preanalysis-wildcard-left-id-1",
        new String[] {
          "가역,1,1,0,NNG,지명,T,가역,Preanalysis,*,*,가/NNG/*+역/NNG/*",
          "나역,1,1,0,NNP,지명,T,나역,Preanalysis,*,*,나/NNP/지명+역/NNG/*"
        },
        POS.Tag.NNG,
        POS.Tag.NNP);
  }
}
