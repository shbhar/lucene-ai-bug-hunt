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
package org.apache.lucene.search.suggest.tst;

import java.io.StringReader;
import org.apache.lucene.search.suggest.FileDictionary;
import org.apache.lucene.store.ByteBuffersDataOutput;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestTSTLookupStoreLoadSupplementary extends LuceneTestCase {

  public void testStoreLoadKeepsSupplementaryKey() throws Exception {
    String key = "\uD842\uDFB7\u91CE\u5BB6";
    try (Directory dir = newDirectory()) {
      TSTLookup lookup = new TSTLookup(dir, "tst");
      lookup.build(new FileDictionary(new StringReader(key + "\t5")));
      ByteBuffersDataOutput out = new ByteBuffersDataOutput();
      lookup.store(out);
      TSTLookup loaded = new TSTLookup(dir, "tst");
      loaded.load(out.toDataInput());
      // key starts with U+20BB7; expected get() after store/load to be 5, actual null
      assertEquals(Long.valueOf(5), loaded.get(key));
    }
  }
}
