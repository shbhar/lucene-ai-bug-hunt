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

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.lucene.analysis.morph.BinaryDictionary;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IntsRef;

public class TestNoriBuilderRightIdLimit extends LuceneTestCase {

  public void testRightId16385() throws Exception {
    Path dir = createTempDir();
    Files.writeString(dir.resolve("test.csv"), "가나,1,16385,100,NNG,*,F,가나,*,*,*,*\n");
    Files.writeString(dir.resolve("char.def"), "DEFAULT 0 1 0\n");
    Files.writeString(dir.resolve("unk.def"), "DEFAULT,1,1,0,SY,*,*,*,*,*,*,*\n");
    Files.writeString(dir.resolve("matrix.def"), "16386 2\n");
    try {
      DictionaryBuilder.build(dir, dir, "utf-8", false);
    } catch (IllegalArgumentException e) {
      return;
    }
    String base = TokenInfoDictionary.class.getName().replace('.', '/');
    TokenInfoDictionary dict =
        new TokenInfoDictionary(
            dir.resolve(base + BinaryDictionary.TARGETMAP_FILENAME_SUFFIX),
            dir.resolve(base + BinaryDictionary.POSDICT_FILENAME_SUFFIX),
            dir.resolve(base + BinaryDictionary.DICT_FILENAME_SUFFIX),
            dir.resolve(base + TokenInfoDictionary.FST_FILENAME_SUFFIX));
    IntsRef ids = new IntsRef();
    dict.lookupWordIds(0, ids);
    // expected: the row is rejected or right id 16385 reads back; actual: it reads back as 1
    assertEquals(16385, dict.getRightId(ids.ints[ids.offset]));
  }
}
