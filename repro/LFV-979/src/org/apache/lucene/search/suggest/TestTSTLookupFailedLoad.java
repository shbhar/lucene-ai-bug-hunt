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
package org.apache.lucene.search.suggest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import org.apache.lucene.search.spell.PlainTextDictionary;
import org.apache.lucene.search.suggest.tst.TSTLookup;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestTSTLookupFailedLoad extends LuceneTestCase {

  public void testFailedLoadIntoNewLookup() throws Exception {
    ByteArrayOutputStream stored = new ByteArrayOutputStream();
    try (Directory dir = new ByteBuffersDirectory()) {
      TSTLookup built = new TSTLookup(dir, "suggest");
      built.build(new PlainTextDictionary(new StringReader("a\nb\n")).getEntryIterator());
      built.store(stored);
    }
    byte[] cutShort = Arrays.copyOf(stored.toByteArray(), stored.size() - 1);

    TSTLookup lookup = new TSTLookup();
    expectThrows(IOException.class, () -> lookup.load(new ByteArrayInputStream(cutShort)));
    // expected: a load() that threw leaves the new lookup empty (0); actual: the stored copy's count
    assertEquals("getCount() after a failed load()", 0, lookup.getCount());
  }
}
