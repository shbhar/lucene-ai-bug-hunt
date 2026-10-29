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
package org.apache.lucene.search.spell;

import java.io.IOException;
import java.io.StringReader;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestSpellCheckerFailedIndexDictionary extends LuceneTestCase {

  public void testRetryAfterFailedIndexDictionary() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        SpellChecker spellChecker = new SpellChecker(dir)) {
      Dictionary failing =
          () -> {
            throw new IOException("simulated read error");
          };
      expectThrows(
          IOException.class,
          () -> spellChecker.indexDictionary(failing, new IndexWriterConfig(), false));
      try {
        spellChecker.indexDictionary(
            new PlainTextDictionary(new StringReader("hello\n")), new IndexWriterConfig(), false);
      } catch (LockObtainFailedException e) {
        throw new AssertionError(
            "expected the retry to succeed, but the failed call's writer still holds the lock", e);
      }
      assertTrue(spellChecker.exist("hello"));
    }
  }
}
