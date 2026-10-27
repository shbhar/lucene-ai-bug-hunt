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
import java.util.Arrays;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.tests.store.MockDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * indexDictionary skips a word when the SpellChecker's cached searcher already contains it, but the
 * words are written by a new IndexWriter whose starting content can differ from that searcher: with
 * OpenMode.CREATE, after a clearIndex() whose searcher reopen failed, or on a retry after an
 * indexDictionary() whose searcher reopen failed.
 */
public class TestSpellCheckerStaleDedup extends LuceneTestCase {

  /** One IOException while the SpellChecker reopens its searcher after a change. */
  private static final class FailReopeningSearcher extends MockDirectoryWrapper.Failure {
    @Override
    public void eval(MockDirectoryWrapper dir) throws IOException {
      if (doFail
          && callStackContains(SpellChecker.class, "swapSearcher")
          && callStackContains(MockDirectoryWrapper.class, "openInput")) {
        doFail = false;
        throw new IOException("simulated I/O error while reopening the spell index");
      }
    }
  }

  private static PlainTextDictionary words(String... words) {
    return new PlainTextDictionary(new StringReader(String.join("\n", words) + "\n"));
  }

  /** No failure at all: an OpenMode.CREATE config rebuilds the index but skips the old words. */
  public void testCreateModeDropsWordsThatWereInTheOldIndex() throws Exception {
    MockDirectoryWrapper dir = newMockDirectory();
    SpellChecker spellChecker = new SpellChecker(dir);
    spellChecker.indexDictionary(words("apple", "banana"), new IndexWriterConfig(null), false);

    spellChecker.indexDictionary(
        words("apple", "cherry"), new IndexWriterConfig(null).setOpenMode(OpenMode.CREATE), false);
    boolean cherry = spellChecker.exist("cherry");
    boolean apple = spellChecker.exist("apple");
    spellChecker.close();
    dir.close();
    assertTrue(cherry);
    assertTrue("apple is in the dictionary that was just indexed", apple);
  }

  public void testIndexDictionaryAfterFailedClearIndexDropsWords() throws Exception {
    MockDirectoryWrapper dir = newMockDirectory();
    FailReopeningSearcher failure = new FailReopeningSearcher();
    dir.failOn(failure);
    SpellChecker spellChecker = new SpellChecker(dir);
    spellChecker.indexDictionary(words("apple", "banana"), new IndexWriterConfig(null), false);

    failure.setDoFail();
    expectThrows(IOException.class, spellChecker::clearIndex);

    spellChecker.indexDictionary(words("apple", "cherry"), new IndexWriterConfig(null), false);
    boolean cherry = spellChecker.exist("cherry");
    boolean apple = spellChecker.exist("apple");
    String[] suggestions = spellChecker.suggestSimilar("appel", 5);
    spellChecker.close();
    dir.close();
    assertTrue(cherry);
    assertTrue(
        "apple is in the dictionary that was just indexed; suggestSimilar(appel)="
            + Arrays.toString(suggestions),
        apple);
  }

  public void testRetryAfterFailedIndexDictionaryIndexesEveryWordTwice() throws Exception {
    MockDirectoryWrapper dir = newMockDirectory();
    FailReopeningSearcher failure = new FailReopeningSearcher();
    dir.failOn(failure);
    SpellChecker spellChecker = new SpellChecker(dir);

    failure.setDoFail();
    expectThrows(
        IOException.class,
        () ->
            spellChecker.indexDictionary(
                words("relieve", "receive"), new IndexWriterConfig(null), false));
    // retry the failed call
    spellChecker.indexDictionary(words("relieve", "receive"), new IndexWriterConfig(null), false);

    String[] suggestions = spellChecker.suggestSimilar("recieve", 2);
    spellChecker.close();
    dir.close();
    Arrays.sort(suggestions);
    assertEquals(
        "each word is indexed once", Arrays.asList("receive", "relieve"), Arrays.asList(suggestions));
  }
}
