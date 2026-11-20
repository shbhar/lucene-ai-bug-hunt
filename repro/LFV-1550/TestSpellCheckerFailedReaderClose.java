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
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.store.MockDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestSpellCheckerFailedReaderClose extends LuceneTestCase {

  // expected: exist is true and no file stays open; actual: AlreadyClosedException, two files open
  public void testOldReaderCloseFailsDuringSwap() throws Exception {
    try (MockDirectoryWrapper dir = new MockDirectoryWrapper(random(), new ByteBuffersDirectory());
        SpellChecker spellChecker = new SpellChecker(dir)) {
      spellChecker.indexDictionary(
          new PlainTextDictionary(new StringReader("apple")), new IndexWriterConfig(), false);
      dir.failOn(
          new MockDirectoryWrapper.Failure() {
            boolean failed;

            @Override
            public void eval(MockDirectoryWrapper d) throws IOException {
              if (failed == false
                  && callStackContains(SpellChecker.class, "swapSearcher")
                  && callStackContains(IndexReader.class, "close")) {
                failed = true;
                throw new IOException("injected: a file of the old reader failed to close");
              }
            }
          });
      expectThrows(
          IOException.class,
          () ->
              spellChecker.indexDictionary(
                  new PlainTextDictionary(new StringReader("banana")),
                  new IndexWriterConfig(),
                  false));
      assertTrue(spellChecker.exist("banana"));
    }
  }
}
