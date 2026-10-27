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
import java.io.Reader;
import java.io.StringReader;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * SpellChecker.indexDictionary opens an IndexWriter on the spell index and closes it only on the
 * success path. If reading the dictionary fails (here: an I/O error from the dictionary's Reader),
 * the writer is never closed, so the spell index stays write-locked: a retry of indexDictionary, or
 * clearIndex(), fails with LockObtainFailedException for the rest of the JVM's life.
 */
public class TestSpellCheckerIndexDictionaryFailure extends LuceneTestCase {

  /** a Reader that returns some text and then fails, like a dictionary file with a read error */
  private static Reader failingReader() {
    return new StringReader("alpha\nbravo\ncharlie\n") {
      int calls;

      @Override
      public int read(char[] cbuf, int off, int len) throws IOException {
        if (calls++ > 0) {
          throw new IOException("simulated read error in the dictionary source");
        }
        return super.read(cbuf, off, Math.min(len, 6)); // "alpha\n"
      }
    };
  }

  public void testFailedIndexDictionaryReleasesSpellIndex() throws Exception {
    try (Directory dir = FSDirectory.open(createTempDir("spell"))) {
      SpellChecker spellChecker = new SpellChecker(dir);
      try {
        IOException e =
            expectThrows(
                IOException.class,
                () ->
                    spellChecker.indexDictionary(
                        new PlainTextDictionary(failingReader()), new IndexWriterConfig(null), false));
        assertEquals("simulated read error in the dictionary source", e.getMessage());

        // The failed call must not keep the spell index locked: retrying with a good dictionary
        // has to work.
        LockObtainFailedException leaked = null;
        try {
          spellChecker.indexDictionary(
              new PlainTextDictionary(new StringReader("delta\necho\n")),
              new IndexWriterConfig(null),
              false);
        } catch (LockObtainFailedException lofe) {
          leaked = lofe;
        }
        assertNull(
            "indexDictionary failed and left its IndexWriter open (spell index still write-locked): "
                + leaked,
            leaked);
        assertTrue(spellChecker.exist("delta"));
      } finally {
        spellChecker.close();
      }
    }
  }

  /**
   * A dictionary word longer than IndexWriter's term length limit (32766 bytes) is rejected by
   * addDocument with IllegalArgumentException; indexDictionary propagates it and keeps the writer.
   */
  public void testImmenseWordReleasesSpellIndex() throws Exception {
    try (Directory dir = FSDirectory.open(createTempDir("spell"))) {
      SpellChecker spellChecker = new SpellChecker(dir);
      try {
        String immense = "a".repeat(40_000);
        expectThrows(
            IllegalArgumentException.class,
            () ->
                spellChecker.indexDictionary(
                    new PlainTextDictionary(new StringReader("alpha\n" + immense + "\n")),
                    new IndexWriterConfig(null),
                    false));
        LockObtainFailedException leaked = null;
        try {
          spellChecker.indexDictionary(
              new PlainTextDictionary(new StringReader("delta\necho\n")),
              new IndexWriterConfig(null),
              false);
        } catch (LockObtainFailedException lofe) {
          leaked = lofe;
        }
        assertNull(
            "indexDictionary failed and left its IndexWriter open (spell index still write-locked): "
                + leaked,
            leaked);
      } finally {
        spellChecker.close();
      }
    }
  }
}
