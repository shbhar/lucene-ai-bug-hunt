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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.lucene.analysis.ko.POS;
import org.apache.lucene.analysis.morph.BinaryDictionary;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IntsRef;

public class TestNoriBuilderLeftIdPosCollision extends LuceneTestCase {

  private static Path file(Path dir, String suffix) {
    return dir.resolve(TokenInfoDictionary.class.getName().replace('.', '/') + suffix);
  }

  public void testEachWordKeepsItsPartOfSpeech() throws Exception {
    Path dir = createTempDir();
    Files.write(
        dir.resolve("test.csv"),
        List.of("가,1,1,0,NNG,*,F,가,*,*,*,*", "는,1,1,0,JX,*,T,는,*,*,*,*"),
        StandardCharsets.UTF_8);
    Files.write(dir.resolve("char.def"), List.of("DEFAULT 0 1 0"));
    Files.write(dir.resolve("unk.def"), List.of("DEFAULT,2,2,10000,SY,*,*,*,*,*,*,*"));
    Files.write(dir.resolve("matrix.def"), List.of("3 3"));
    try {
      DictionaryBuilder.build(dir, dir, "utf-8", false);
    } catch (IllegalArgumentException rejected) {
      return;
    }
    TokenInfoDictionary dict =
        new TokenInfoDictionary(
            file(dir, BinaryDictionary.TARGETMAP_FILENAME_SUFFIX),
            file(dir, BinaryDictionary.POSDICT_FILENAME_SUFFIX),
            file(dir, BinaryDictionary.DICT_FILENAME_SUFFIX),
            file(dir, TokenInfoDictionary.FST_FILENAME_SUFFIX));
    IntsRef ids = new IntsRef();
    dict.lookupWordIds(0, ids);
    // expected: an IllegalArgumentException from build, or 가 (source id 0) keeps NNG; actual: JX
    assertEquals(POS.Tag.NNG, dict.getMorphAttributes().getLeftPOS(ids.ints[ids.offset]));
  }
}
