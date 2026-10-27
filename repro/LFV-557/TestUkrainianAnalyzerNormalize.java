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
package org.apache.lucene.analysis.uk;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.TermToBytesRefAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * UkrainianMorfologikAnalyzer lower-cases every token (LowerCaseFilter) and maps apostrophe
 * variants, the stress mark, the soft hyphen and ґ (MappingCharFilter in initReader) before
 * lemmatization, but it overrides neither normalize(String, TokenStream) nor
 * initReaderForNormalization, so Analyzer#normalize returns its input unchanged.
 */
public class TestUkrainianAnalyzerNormalize extends LuceneTestCase {

  private static BytesRef singleToken(Analyzer a, String text) throws Exception {
    try (TokenStream ts = a.tokenStream("f", text)) {
      TermToBytesRefAttribute term = ts.addAttribute(TermToBytesRefAttribute.class);
      ts.reset();
      assertTrue(ts.incrementToken());
      BytesRef t = BytesRef.deepCopyOf(term.getBytesRef());
      assertFalse("expected a single token for " + text, ts.incrementToken());
      ts.end();
      return t;
    }
  }

  /** Words the dictionary passes through unchanged (lemma == lower-cased, mapped surface form). */
  public void testNormalizeEqualsIndexedTerm() throws Exception {
    try (Analyzer a = new UkrainianMorfologikAnalyzer()) {
      for (String w : new String[] {"Lucene", "м’ясо", "СІМ’Я"}) {
        BytesRef indexed = singleToken(a, w);
        BytesRef normalized = a.normalize("f", w);
        assertEquals(
            "normalize(\"" + w + "\")=" + normalized.utf8ToString() + " but the index holds " + indexed.utf8ToString(),
            indexed,
            normalized);
      }
    }
  }

  /** A prefix of the word exactly as the document spells it (typographic apostrophe U+2019). */
  public void testPrefixQueryFindsDocument() throws Exception {
    try (Analyzer a = new UkrainianMorfologikAnalyzer();
        Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
        Document doc = new Document();
        doc.add(new TextField("body", "м’ясо Lucene", Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        QueryParser qp = new QueryParser("body", a);
        assertEquals(1, s.count(qp.parse("м'яс*"))); // control: ASCII apostrophe
        assertEquals(1, s.count(qp.parse("luc*"))); // control: lower case
        assertEquals(qp.parse("м’яс*").toString(), 1, s.count(qp.parse("м’яс*")));
        assertEquals(qp.parse("Luc*").toString(), 1, s.count(qp.parse("Luc*")));
      }
    }
  }
}
