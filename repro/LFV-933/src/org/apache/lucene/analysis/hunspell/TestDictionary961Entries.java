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
package org.apache.lucene.analysis.hunspell;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestDictionary961Entries extends LuceneTestCase {

  public void testDictionaryWith961WordsLoads() throws Exception {
    StringBuilder dic = new StringBuilder("961\n");
    for (int i = 0; i < 940; i++) {
      dic.append((char) ('a' + i / 676))
          .append((char) ('a' + i / 26 % 26))
          .append((char) ('a' + i % 26))
          .append('\n');
    }
    for (char c = 'a'; c <= 'u'; c++) {
      dic.append(c).append("ing\n");
    }
    try {
      new Dictionary(
          new ByteBuffersDirectory(),
          "dict",
          new ByteArrayInputStream("SET UTF-8\n".getBytes(UTF_8)),
          new ByteArrayInputStream(dic.toString().getBytes(UTF_8)));
    } catch (RuntimeException e) {
      // expected: 940 three-letter words plus 21 "-ing" words load; actual: "Too many collisions"
      throw new AssertionError("expected the 961-word dictionary to load, but: " + e, e);
    }
  }
}
