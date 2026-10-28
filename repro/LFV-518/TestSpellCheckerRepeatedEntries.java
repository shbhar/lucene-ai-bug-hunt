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
import java.util.HashSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.suggest.DocumentDictionary;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * SpellChecker.indexDictionary skips words that are already in the spell index (LUCENE-883:
 * "consecutive calls to Spellchecker.indexDictionary() won't insert terms twice anymore"), but a
 * word that occurs twice within ONE dictionary is indexed twice. Uses only public API.
 */
public class TestSpellCheckerRepeatedEntries extends LuceneTestCase {

  private static int docFreqInSpellIndex(Directory spellDir, String word) throws IOException {
    try (DirectoryReader r = DirectoryReader.open(spellDir)) {
      return r.docFreq(new Term(SpellChecker.F_WORD, word));
    }
  }

  private static int numDocsInSpellIndex(Directory spellDir) throws IOException {
    try (DirectoryReader r = DirectoryReader.open(spellDir)) {
      return r.numDocs();
    }
  }

  /** Repeated line in a PlainTextDictionary: the word should be indexed once. */
  public void testRepeatedLineIndexedOnce() throws IOException {
    try (Directory spellDir = newDirectory();
        SpellChecker sc = new SpellChecker(spellDir)) {
      sc.indexDictionary(
          new PlainTextDictionary(new StringReader("lantern\nlantern\npattern\n")),
          new IndexWriterConfig(),
          false);
      int df = docFreqInSpellIndex(spellDir, "lantern");
      int n = numDocsInSpellIndex(spellDir);
      assertEquals("docs for 'lantern' in the spell index (numDocs=" + n + ")", 1, df);
      assertEquals("spell index docs for 2 distinct words", 2, n);
    }
  }

  /** The duplicate shows up in suggestSimilar and pushes out the distinct word. */
  public void testSuggestionsHaveNoDuplicates() throws IOException {
    try (Directory spellDir = newDirectory();
        SpellChecker sc = new SpellChecker(spellDir)) {
      sc.indexDictionary(
          new PlainTextDictionary(new StringReader("lantern\nlantern\npattern\n")),
          new IndexWriterConfig(),
          false);
      String[] sugg = sc.suggestSimilar("lanterm", 2);
      assertEquals(
          "suggestions " + Arrays.toString(sugg) + " contain a duplicate",
          sugg.length,
          new HashSet<>(Arrays.asList(sugg)).size());
      assertEquals(Arrays.asList("lantern", "pattern"), Arrays.asList(sugg));
    }
  }

  /** Same scenario through a DocumentDictionary: two user docs share a stored title. */
  public void testDocumentDictionaryRepeatedValueIndexedOnce() throws IOException {
    try (Directory userDir = newDirectory();
        Directory spellDir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(userDir, new IndexWriterConfig())) {
        for (String title : new String[] {"lantern", "pattern", "lantern"}) {
          Document d = new Document();
          d.add(new StoredField("title", title));
          w.addDocument(d);
        }
      }
      try (DirectoryReader userReader = DirectoryReader.open(userDir);
          SpellChecker sc = new SpellChecker(spellDir)) {
        sc.indexDictionary(
            new DocumentDictionary(userReader, "title", null), new IndexWriterConfig(), false);
        assertEquals(
            "docs for 'lantern' in the spell index", 1, docFreqInSpellIndex(spellDir, "lantern"));
      }
    }
  }

  /** Control: the same words split across two calls are de-duplicated (LUCENE-883). */
  public void testControlSplitAcrossCallsIndexedOnce() throws IOException {
    try (Directory spellDir = newDirectory();
        SpellChecker sc = new SpellChecker(spellDir)) {
      sc.indexDictionary(
          new PlainTextDictionary(new StringReader("lantern\npattern\n")),
          new IndexWriterConfig(),
          false);
      sc.indexDictionary(
          new PlainTextDictionary(new StringReader("lantern\n")), new IndexWriterConfig(), false);
      assertEquals(1, docFreqInSpellIndex(spellDir, "lantern"));
      assertEquals(2, numDocsInSpellIndex(spellDir));
      String[] sugg = sc.suggestSimilar("lanterm", 2);
      assertEquals(Arrays.asList("lantern", "pattern"), Arrays.asList(sugg));
    }
  }
}
