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
package org.apache.lucene.analysis.ja.dict;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.lucene.analysis.morph.BinaryDictionary;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IntsRef;

public class TestDictionaryBuilderLongReading extends LuceneTestCase {

  public void testReadingOf128Chars() throws Exception {
    String reading = "カ".repeat(128);
    Path dir = createTempDir();
    String row = "漢字,1,1,1,名詞,一般,*,*,*,*,*," + reading + "," + reading;
    Files.writeString(dir.resolve("test.csv"), row);
    Files.createFile(dir.resolve("unk.def"));
    Files.createFile(dir.resolve("char.def"));
    Files.writeString(dir.resolve("matrix.def"), "1 1\n");
    try {
      DictionaryBuilder.build(DictionaryBuilder.DictionaryFormat.IPADIC, dir, dir, "utf-8", false);
    } catch (IllegalArgumentException rejected) {
      return; // expected: the row is rejected or its reading reads back; actual: the reading is ""
    }
    String prefix = dir.resolve(TokenInfoDictionary.class.getName().replace('.', '/')).toString();
    TokenInfoDictionary dict =
        new TokenInfoDictionary(
            Path.of(prefix + BinaryDictionary.TARGETMAP_FILENAME_SUFFIX),
            Path.of(prefix + BinaryDictionary.POSDICT_FILENAME_SUFFIX),
            Path.of(prefix + BinaryDictionary.DICT_FILENAME_SUFFIX),
            Path.of(prefix + TokenInfoDictionary.FST_FILENAME_SUFFIX));
    IntsRef wordIds = new IntsRef();
    dict.lookupWordIds(0, wordIds);
    char[] surface = "漢字".toCharArray();
    assertEquals(
        "reading", reading, dict.getMorphAttributes().getReading(wordIds.ints[0], surface, 0, 2));
  }
}
