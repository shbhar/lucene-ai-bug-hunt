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
import java.util.List;
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
 * SpellChecker.indexDictionary skips words that are already in the spell index, but it checks only
 * the index as it was before the call. A word that one Dictionary yields twice is indexed twice,
 * and suggestSimilar then returns it twice and drops a different word. The same words split over
 * two calls are indexed once (LUCENE-883).
 */
public class TestSpellCheckerRepeatedDictionaryEntries extends LuceneTestCase {

  private static final String WORDS = "relieve\nrelieve\nreceive\n";
  private static final List<String> EXPECTED = Arrays.asList("relieve", "receive");

  private static int spellDocFreq(Directory spellDir, String word) throws IOException {
    try (DirectoryReader r = DirectoryReader.open(spellDir)) {
      return r.docFreq(new Term(SpellChecker.F_WORD, word));
    }
  }

  private static void assertIndexedOnce(SpellChecker sc, Directory spellDir) throws IOException {
    String[] similar = sc.suggestSimilar("recieve", 2);
    int df = spellDocFreq(spellDir, "relieve");
    assertEquals(
        "suggestions " + Arrays.toString(similar) + ", spell-index docs for 'relieve': " + df,
        EXPECTED,
        Arrays.asList(similar));
    assertEquals("spell-index docs for 'relieve'", 1, df);
  }

  /** A word list with a repeated line, indexed into an empty spell index. */
  public void testRepeatedLine() throws IOException {
    try (Directory spellDir = newDirectory();
        SpellChecker sc = new SpellChecker(spellDir)) {
      sc.indexDictionary(
          new PlainTextDictionary(new StringReader(WORDS)), new IndexWriterConfig(), false);
      assertIndexedOnce(sc, spellDir);
    }
  }

  /** The same, into a spell index that already holds a word, so the existence check runs. */
  public void testRepeatedLineIntoNonEmptyIndex() throws IOException {
    try (Directory spellDir = newDirectory();
        SpellChecker sc = new SpellChecker(spellDir)) {
      sc.indexDictionary(
          new PlainTextDictionary(new StringReader("unrelated\n")), new IndexWriterConfig(), false);
      sc.indexDictionary(
          new PlainTextDictionary(new StringReader(WORDS)), new IndexWriterConfig(), false);
      assertIndexedOnce(sc, spellDir);
    }
  }

  /** A DocumentDictionary over a stored field: two documents store the same word. */
  public void testDocumentDictionaryRepeatedValue() throws IOException {
    try (Directory userDir = newDirectory();
        Directory spellDir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(userDir, new IndexWriterConfig())) {
        for (String title : new String[] {"relieve", "relieve", "receive"}) {
          Document doc = new Document();
          doc.add(new StoredField("title", title));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader userReader = DirectoryReader.open(userDir);
          SpellChecker sc = new SpellChecker(spellDir)) {
        sc.indexDictionary(
            new DocumentDictionary(userReader, "title", null), new IndexWriterConfig(), false);
        assertIndexedOnce(sc, spellDir);
      }
    }
  }

  /** Control: the same words split over two calls are indexed once. */
  public void testSplitOverTwoCalls() throws IOException {
    try (Directory spellDir = newDirectory();
        SpellChecker sc = new SpellChecker(spellDir)) {
      sc.indexDictionary(
          new PlainTextDictionary(new StringReader("relieve\n")), new IndexWriterConfig(), false);
      sc.indexDictionary(
          new PlainTextDictionary(new StringReader("relieve\nreceive\n")),
          new IndexWriterConfig(),
          false);
      assertIndexedOnce(sc, spellDir);
    }
  }
}
