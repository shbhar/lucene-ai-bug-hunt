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
package org.apache.lucene.analysis.cn.smart;

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
 * SmartChineseAnalyzer#normalize applies LowerCaseFilter, but the analyzer's own case handling is
 * done by the tokenizer (SegTokenFilter): full-width Latin letters and digits are converted to
 * half-width and only Basic Latin A-Z is lower-cased. The two disagree in both directions.
 */
public class TestSmartChineseNormalize extends LuceneTestCase {

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

  /** Full-width Latin: indexed as half-width, normalize keeps full-width. */
  public void testFullWidthNormalizeEqualsIndexedTerm() throws Exception {
    try (Analyzer a = new SmartChineseAnalyzer()) {
      for (String w : new String[] {"ＡＢＣ", "Ｊａｖａ"}) {
        BytesRef indexed = singleToken(a, w);
        BytesRef normalized = a.normalize("f", w);
        assertEquals(
            "normalize(\"" + w + "\")=" + normalized.utf8ToString() + " but the index holds " + indexed.utf8ToString(),
            indexed,
            normalized);
      }
    }
  }

  /** Non-ASCII capital letters: indexed unchanged, normalize lower-cases them. */
  public void testNonAsciiCapitalNormalizeEqualsIndexedTerm() throws Exception {
    try (Analyzer a = new SmartChineseAnalyzer()) {
      for (String w : new String[] {"É", "Б"}) {
        BytesRef indexed = singleToken(a, w);
        BytesRef normalized = a.normalize("f", w);
        assertEquals(
            "normalize(\"" + w + "\")=" + normalized.utf8ToString() + " but the index holds " + indexed.utf8ToString(),
            indexed,
            normalized);
      }
    }
  }

  /** A prefix of a full-width word, typed as in the document. */
  public void testFullWidthPrefixQueryFindsDocument() throws Exception {
    try (Analyzer a = new SmartChineseAnalyzer();
        Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
        Document doc = new Document();
        doc.add(new TextField("body", "我们使用Ｌｕｃｅｎｅ", Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        QueryParser qp = new QueryParser("body", a);
        assertEquals(1, s.count(qp.parse("Luc*"))); // control: half-width prefix
        assertEquals(qp.parse("Ｌｕｃ*").toString(), 1, s.count(qp.parse("Ｌｕｃ*")));
      }
    }
  }
}
