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
package org.apache.lucene.analysis.es;

import java.io.IOException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * SpanishPluralStemmer's special-case list (plurals whose "-es" ending must be removed) contains the
 * singular "contrarreloj" instead of its plural "contrarrelojes". All other 12 entries are plurals
 * ending in "es" (relojes, bojes, carcajes, ...). As a result the stemmer cuts two letters off the
 * singular ("contrarreloj" -> "contrarrel"), while the plural "contrarrelojes" falls through to the
 * generic rule and becomes "contrarreloje": singular and plural never match.
 */
public class TestSpanishPluralContrarreloj extends BaseTokenStreamTestCase {

  private static Analyzer pluralAnalyzer() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer source = new KeywordTokenizer();
        return new TokenStreamComponents(source, new SpanishPluralStemFilter(source));
      }
    };
  }

  private static String stem(Analyzer a, String word) throws IOException {
    try (TokenStream ts = a.tokenStream("f", word)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      assertTrue(ts.incrementToken());
      String s = term.toString();
      assertFalse(ts.incrementToken());
      ts.end();
      return s;
    }
  }

  /** Control: the analogous -j noun "reloj" / "relojes" (also in the special-case list) works. */
  public void testControlRelojConflates() throws IOException {
    try (Analyzer a = pluralAnalyzer()) {
      checkOneTerm(a, "reloj", "reloj");
      checkOneTerm(a, "relojes", "reloj");
    }
  }

  /** A singular that does not end in "s" is not a plural; the plural stemmer must leave it alone. */
  public void testSingularContrarrelojUnchanged() throws IOException {
    try (Analyzer a = pluralAnalyzer()) {
      checkOneTerm(a, "contrarreloj", "contrarreloj");
    }
  }

  /** The plural "contrarrelojes" (rule: nouns ending in -j add -es) must reduce to the singular. */
  public void testPluralContrarrelojesReducesToSingular() throws IOException {
    try (Analyzer a = pluralAnalyzer()) {
      checkOneTerm(a, "contrarrelojes", "contrarreloj");
    }
  }

  /** End to end: a document containing the plural is not found by the singular. */
  public void testSingularQueryFindsPluralDocument() throws IOException {
    try (Analyzer a = pluralAnalyzer();
        Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
        Document doc = new Document();
        doc.add(new TextField("body", "contrarrelojes", Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        String queryTerm = stem(a, "contrarreloj");
        assertEquals(
            "indexed term " + stem(a, "contrarrelojes") + ", query term " + queryTerm,
            1,
            s.count(new TermQuery(new Term("body", queryTerm))));
      }
    }
  }
}
