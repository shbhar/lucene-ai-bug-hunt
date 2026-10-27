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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * hunspell(5) COMPLEXPREFIXES: "Set twofold prefix stripping (but single suffix stripping)". After
 * removing the second prefix, the stemmer goes on removing prefixes: words with a third prefix are
 * accepted, and a zero-length prefix (affix "0") is removed again and again until
 * StackOverflowError, in spell(), getRoots() and HunspellStemFilter. Uses only public API.
 */
public class TestComplexPrefixesThirdPrefix extends LuceneTestCase {

  private static Dictionary dictionary(String aff, String dic) throws Exception {
    try (InputStream a = new ByteArrayInputStream(aff.getBytes(StandardCharsets.UTF_8));
        InputStream d = new ByteArrayInputStream(dic.getBytes(StandardCharsets.UTF_8))) {
      return new Dictionary(a, List.of(d), false, SortingStrategy.inMemory());
    }
  }

  /** Three chained prefixes a, b, c: x -> ax -> bax are twofold; cbax would need a third prefix. */
  public void testAtMostTwoPrefixes() throws Exception {
    Hunspell h =
        new Hunspell(
            dictionary(
                """
                COMPLEXPREFIXES
                PFX A Y 1
                PFX A 0 a/B .
                PFX B Y 1
                PFX B 0 b/C .
                PFX C Y 1
                PFX C 0 c .
                """,
                "1\nx/A\n"));
    assertTrue(h.spell("ax"));
    assertTrue(h.spell("bax"));
    assertFalse("cbax needs three prefixes", h.spell("cbax"));
  }

  /** A zero-length prefix A that enables prefix B ("flag via empty affix"): bx = B + A + x. */
  private static final String ZERO_AFF =
      """
      COMPLEXPREFIXES
      PFX A Y 1
      PFX A 0 0/B .
      PFX B Y 1
      PFX B 0 b .
      """;

  private static final String ZERO_DIC = "1\nx/A\n";

  public void testZeroLengthPrefixSpell() throws Exception {
    Hunspell h = new Hunspell(dictionary(ZERO_AFF, ZERO_DIC));
    assertTrue(h.spell("bx"));
    assertFalse("bbx needs B twice", h.spell("bbx"));
  }

  public void testZeroLengthPrefixStemming() throws Exception {
    Hunspell h = new Hunspell(dictionary(ZERO_AFF, ZERO_DIC));
    assertEquals(List.of("x"), h.getRoots("bx"));
    assertEquals(List.of(), h.getRoots("bbx"));
  }

  /** The same dictionary in an analysis chain: indexing "bbx" must not fail (or close the writer). */
  public void testZeroLengthPrefixStemFilterIndexing() throws Exception {
    Dictionary d = dictionary(ZERO_AFF, ZERO_DIC);
    Analyzer analyzer =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer t = new WhitespaceTokenizer();
            return new TokenStreamComponents(t, new HunspellStemFilter(t, d));
          }
        };
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
      Document doc = new Document();
      doc.add(new TextField("body", "bx bbx", Field.Store.NO));
      w.addDocument(doc);
      w.commit();
      assertEquals(1, w.getDocStats().numDocs);
    } finally {
      analyzer.close();
    }
  }
}
