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
package org.apache.lucene.analysis.sr;

import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.TermToBytesRefAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * SerbianAnalyzer indexes every term through SerbianNormalizationFilter (Cyrillic to "bald" Latin,
 * diacritics removed), but SerbianAnalyzer#normalize only lower-cases. Analyzer#normalize promises
 * "the representation that it would have in the index", and the query parsers use it for prefix,
 * wildcard, fuzzy, regexp and range terms.
 */
public class TestSerbianAnalyzerNormalize extends LuceneTestCase {

  private static BytesRef singleToken(Analyzer a, String text) throws Exception {
    try (TokenStream ts = a.tokenStream("f", text)) {
      TermToBytesRefAttribute term = ts.addAttribute(TermToBytesRefAttribute.class);
      ts.reset();
      assertTrue(ts.incrementToken());
      BytesRef t = BytesRef.deepCopyOf(term.getBytesRef());
      assertFalse(ts.incrementToken());
      ts.end();
      return t;
    }
  }

  /** Stemming is switched off for these words (documented stem exclusion set). */
  public void testNormalizeEqualsIndexedTermWithoutStemming() throws Exception {
    CharArraySet noStem = new CharArraySet(List.of("ђак", "čačak", "beograd", "београд"), false);
    try (Analyzer a = new SerbianAnalyzer(CharArraySet.EMPTY_SET, noStem)) {
      for (String w : new String[] {"Ђак", "Čačak", "Београд", "Beograd"}) {
        BytesRef indexed = singleToken(a, w);
        BytesRef normalized = a.normalize("f", w);
        assertEquals(
            "normalize(\"" + w + "\")=" + normalized.utf8ToString() + " but the index holds " + indexed.utf8ToString(),
            indexed,
            normalized);
      }
    }
  }

  /** End to end with the default SerbianAnalyzer: a prefix of the word as it appears in the text. */
  public void testPrefixQueryInCyrillicFindsCyrillicDocument() throws Exception {
    try (Analyzer a = new SerbianAnalyzer();
        Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
        Document doc = new Document();
        doc.add(new TextField("body", "Београд", Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        QueryParser classic = new QueryParser("body", a);
        StandardQueryParser flexible = new StandardQueryParser(a);
        // control: the same prefix typed in "bald" Latin reaches the document
        assertEquals(1, s.count(classic.parse("Beogr*")));
        // the document's own spelling must reach it too
        assertEquals("classic " + classic.parse("Беогр*"), 1, s.count(classic.parse("Беогр*")));
        assertEquals("flexible " + flexible.parse("Беогр*", "body"), 1, s.count(flexible.parse("Беогр*", "body")));
        assertEquals("classic " + classic.parse("Београд~1"), 1, s.count(classic.parse("Београд~1")));
      }
    }
  }

  /** Latin script with diacritics: index holds "cack" (stem of čačak, diacritics removed). */
  public void testPrefixQueryWithDiacriticsFindsDocument() throws Exception {
    try (Analyzer a = new SerbianAnalyzer();
        Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
        Document doc = new Document();
        doc.add(new TextField("body", "Čačak", Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        QueryParser classic = new QueryParser("body", a);
        assertEquals(1, s.count(classic.parse("Cac*"))); // control
        assertEquals("classic " + classic.parse("Čač*"), 1, s.count(classic.parse("Čač*")));
      }
    }
  }
}
