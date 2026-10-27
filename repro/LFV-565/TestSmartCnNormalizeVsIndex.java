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

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;
import org.junit.Test;

/**
 * Analyzer#normalize(String, String) is documented to return "the representation that it would
 * have in the index". For SmartChineseAnalyzer a single full-width Latin word is indexed in
 * half-width lower case (SegTokenFilter), so normalize must return the same bytes, and a
 * prefix/fuzzy query built through the classic QueryParser (which calls Analyzer#normalize) must
 * find the document a plain term query finds.
 *
 * <p>Uses only public API; plain JUnit 4 so the same source compiles against older releases.
 */
public class TestSmartCnNormalizeVsIndex {

  private static List<String> indexedTerms(Analyzer a, String text) throws IOException {
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

  /** Assert the input is one indexed token and that normalize() yields exactly that token. */
  private static void assertNormalizeMatchesIndex(String input) throws IOException {
    try (Analyzer a = new SmartChineseAnalyzer()) {
      List<String> idx = indexedTerms(a, input);
      // precondition: the whole input is a single indexed token (no tokenization involved)
      assertEquals("precondition: single indexed token for " + input, 1, idx.size());
      BytesRef expected = new BytesRef(idx.get(0));
      BytesRef actual = a.normalize("body", input);
      assertEquals(
          "normalize(\""
              + input
              + "\")=\""
              + actual.utf8ToString()
              + "\" but the indexed term is \""
              + idx.get(0)
              + "\"",
          expected,
          actual);
    }
  }

  @Test
  public void testFullWidthLettersNormalizeToIndexedForm() throws IOException {
    assertNormalizeMatchesIndex("ＡＢＣ"); // full-width "ABC", indexed as "abc"
  }

  @Test
  public void testFullWidthDigitsNormalizeToIndexedForm() throws IOException {
    assertNormalizeMatchesIndex("１２３"); // full-width "123", indexed as "123"
  }

  @Test
  public void testNonAsciiCapitalNormalizeToIndexedForm() throws IOException {
    assertNormalizeMatchesIndex("É"); // LATIN CAPITAL E WITH ACUTE, indexed unchanged
  }

  @Test
  public void testHalfWidthControlNormalizeToIndexedForm() throws IOException {
    // control: Basic Latin is handled identically on both sides; this one must pass
    assertNormalizeMatchesIndex("ABC");
  }

  private static int count(Directory dir, Query q) throws IOException {
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      return new IndexSearcher(r).count(q);
    }
  }

  @Test
  public void testFullWidthPrefixAndFuzzyQueriesFindDocument() throws Exception {
    try (Analyzer a = new SmartChineseAnalyzer();
        Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
        Document doc = new Document();
        // "we use Lucene" with "Lucene" typed in full-width letters
        doc.add(
            new TextField(
                "body",
                "我们使用Ｌｕｃｅｎｅ",
                Field.Store.NO));
        w.addDocument(doc);
      }
      // preconditions: the indexed term is "lucen" (half-width, lower-cased, Porter-stemmed),
      // and the same full-width word as a plain term query finds the document
      assertEquals(1, count(dir, new TermQuery(new Term("body", "lucen"))));
      QueryParser qp = new QueryParser("body", a);
      String fwLucene = "Ｌｕｃｅｎｅ";
      assertEquals("plain full-width term query", 1, count(dir, qp.parse(fwLucene)));
      // controls: half-width multi-term queries find the document
      assertEquals("control Luc*", 1, count(dir, qp.parse("Luc*")));
      assertEquals("control Lucen~1", 1, count(dir, qp.parse("Lucen~1")));
      // the bug: the same multi-term queries typed in full-width letters
      Query prefix = qp.parse("Ｌｕｃ*"); // full-width "Luc*"
      Query fuzzy = qp.parse("Ｌｕｃｅｎ~1"); // full-width "Lucen~1"
      int prefixHits = count(dir, prefix);
      int fuzzyHits = count(dir, fuzzy);
      assertEquals(
          "prefix " + prefix + " hits=" + prefixHits + ", fuzzy " + fuzzy + " hits=" + fuzzyHits,
          "1/1",
          prefixHits + "/" + fuzzyHits);
    }
  }
}
