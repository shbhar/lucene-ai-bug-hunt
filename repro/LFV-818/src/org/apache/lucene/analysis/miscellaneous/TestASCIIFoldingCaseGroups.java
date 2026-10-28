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
package org.apache.lucene.analysis.miscellaneous;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.QueryBuilder;

/**
 * ASCIIFoldingFilter keeps the case of every letter it folds (a with grave folds to 'a', A with grave
 * to 'A'), except for seven table entries that sit in the output group of the other case:
 *
 * <ul>
 *   <li>U+01E5 LATIN SMALL LETTER G WITH STROKE and U+01E7 LATIN SMALL LETTER G WITH CARON fold to
 *       'G' (their capitals U+01E4 and U+01E6 also fold to 'G');
 *   <li>U+A785 LATIN SMALL LETTER INSULAR S folds to 'S' and U+A784 LATIN CAPITAL LETTER INSULAR S
 *       to 's' (the other insular pairs D, F, G, R and T are correct);
 *   <li>U+0297 LATIN LETTER STRETCHED C (a lowercase letter) folds to 'C';
 *   <li>U+2C6F LATIN CAPITAL LETTER TURNED A folds to 'a' and U+A73E LATIN CAPITAL LETTER REVERSED C
 *       WITH DOT to 'c'.
 * </ul>
 *
 * With the usual LowerCaseFilter-then-ASCIIFoldingFilter chain the first five produce an upper-case
 * ASCII letter in the indexed term, which no query typed in ASCII can match. The last two show in a
 * chain that keeps case.
 */
public class TestASCIIFoldingCaseGroups extends LuceneTestCase {

  /** StandardTokenizer, then LowerCaseFilter, then ASCIIFoldingFilter, at index and query time. */
  private static Analyzer lowercaseThenFold(boolean preserveOriginal) {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer t = new StandardTokenizer();
        TokenStream ts = new LowerCaseFilter(t);
        return new TokenStreamComponents(t, new ASCIIFoldingFilter(ts, preserveOriginal));
      }
    };
  }

  /** WhitespaceTokenizer, then ASCIIFoldingFilter: a chain that keeps case. */
  private static Analyzer foldOnly() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer t = new WhitespaceTokenizer();
        return new TokenStreamComponents(t, new ASCIIFoldingFilter(t));
      }
    };
  }

  private static List<String> terms(Analyzer a, String text) throws IOException {
    List<String> out = new ArrayList<>();
    try (TokenStream ts = a.tokenStream("body", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        out.add(term.toString());
      }
      ts.end();
    }
    return out;
  }

  /** Indexes docText and runs the query typed as asciiQuery; returns the hit count. */
  private int hits(Analyzer a, String docText, String asciiQuery) throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
        Document doc = new Document();
        doc.add(new TextField("body", docText, Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        Query q = new QueryBuilder(a).createBooleanQuery("body", asciiQuery);
        return new IndexSearcher(r).count(q);
      }
    }
  }

  /** {document text, query typed with the letter's ASCII base} */
  private void check(Analyzer a, String[][] cases) throws IOException {
    List<String> failures = new ArrayList<>();
    for (String[] c : cases) {
      int n = hits(a, c[0], c[1]);
      if (n != 1) {
        failures.add(
            String.format(
                "%s %s: indexed as %s, ASCII query '%s' analyzed as %s, hits=%d",
                c[2], c[0], terms(a, c[0]), c[1], terms(a, c[1]), n));
      }
    }
    assertTrue(
        failures.size() + " of " + cases.length + " documents not found:\n  " + String.join("\n  ", failures),
        failures.isEmpty());
  }

  private static final String[][] LOWERCASE_CHAIN_CASES = {
    {"naǧib", "nagib", "U+01E7 small g with caron"},
    {"NAǦIB", "nagib", "U+01E6 capital G with caron (lower-cased to U+01E7)"},
    {"aǥa", "aga", "U+01E5 small g with stroke"},
    {"šiõǥǥ", "siogg", "U+01E5 small g with stroke (Skolt Sami word)"},
    {"ꞅet", "set", "U+A785 small insular s"},
    {"Ꞅet", "set", "U+A784 capital insular s (lower-cased to U+A785)"},
    {"ʗa", "ca", "U+0297 stretched c (lowercase letter)"},
    // controls: these fold correctly
    {"ağa", "aga", "control U+011F small g with breve"},
    {"ṡet", "set", "control U+1E61 small s with dot above"},
    {"𐐀é", "𐐀e", "control U+10400 Deseret capital + e with acute"},
  };

  public void testLowercaseThenAsciiFolding() throws IOException {
    try (Analyzer a = lowercaseThenFold(false)) {
      check(a, LOWERCASE_CHAIN_CASES);
    }
  }

  public void testLowercaseThenAsciiFoldingPreserveOriginal() throws IOException {
    try (Analyzer a = lowercaseThenFold(true)) {
      check(a, LOWERCASE_CHAIN_CASES);
    }
  }

  /** The same chain built from the SPI factories ("lowercase", "asciiFolding"). */
  public void testFactoryChain() throws IOException {
    try (Analyzer a =
        CustomAnalyzer.builder()
            .withTokenizer("standard")
            .addTokenFilter("lowercase")
            .addTokenFilter("asciiFolding", "preserveOriginal", "false")
            .build()) {
      check(a, LOWERCASE_CHAIN_CASES);
    }
  }

  /** A case-keeping chain: every listed letter should keep its case. */
  public void testFoldKeepsCase() throws IOException {
    try (Analyzer a = foldOnly()) {
      check(
          a,
          new String[][] {
            {"Ɐb", "Ab", "U+2C6F capital turned A"},
            {"Ꜿb", "Cb", "U+A73E capital reversed C with dot"},
            {"Ꞅb", "Sb", "U+A784 capital insular S"},
            {"ꞅb", "sb", "U+A785 small insular s"},
            {"ǧb", "gb", "U+01E7 small g with caron"},
            {"ǥb", "gb", "U+01E5 small g with stroke"},
            {"ʗb", "cb", "U+0297 stretched c (lowercase letter)"},
            // controls
            {"ɐb", "ab", "control U+0250 small turned a"},
            {"Ǧb", "Gb", "control U+01E6 capital G with caron"},
          });
    }
  }
}
