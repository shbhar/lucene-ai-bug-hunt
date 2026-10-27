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
package org.apache.lucene.analysis.wikipedia.v1;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.analysis.wikipedia.WikipediaTokenizer;
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
 * Independent check (public API only): WikipediaTokenizer emits Chinese/Japanese characters as
 * {@code <CJ>} tokens in plain text, and keeps Latin and Hangul text inside wiki markup; does it
 * also keep Chinese/Japanese text inside wiki markup?
 *
 * <p>The assertion is deliberately weak: every Han/Kana character of the markup content must
 * appear in SOME emitted term (any type, any token split), so it does not presuppose how a fix
 * would type or split those tokens.
 */
public class TestWikipediaMarkupCjk extends BaseTokenStreamTestCase {

  private static final class Tok {
    final String term;
    final String type;

    Tok(String term, String type) {
      this.term = term;
      this.type = type;
    }

    @Override
    public String toString() {
      return term + "|" + type;
    }
  }

  private static List<Tok> tokenize(String text) throws IOException {
    List<Tok> out = new ArrayList<>();
    try (Tokenizer tok = new WikipediaTokenizer()) {
      tok.setReader(new StringReader(text));
      CharTermAttribute term = tok.addAttribute(CharTermAttribute.class);
      TypeAttribute type = tok.addAttribute(TypeAttribute.class);
      tok.reset();
      while (tok.incrementToken()) {
        out.add(new Tok(term.toString(), type.type()));
      }
      tok.end();
    }
    return out;
  }

  /** Every char of {@code content} must occur in some emitted term. */
  private static void assertContentKept(String text, String content) throws IOException {
    List<Tok> toks = tokenize(text);
    StringBuilder all = new StringBuilder();
    for (Tok t : toks) {
      all.append(t.term).append('\u0000');
    }
    StringBuilder missing = new StringBuilder();
    for (int i = 0; i < content.length(); i++) {
      char c = content.charAt(i);
      if (all.indexOf(String.valueOf(c)) < 0) {
        missing.append(c);
      }
    }
    assertTrue(
        "input '" + text + "' -> tokens " + toks + "; characters never emitted: '" + missing + "'",
        missing.length() == 0);
  }

  // ---- controls (expected to pass on the stock jar) ----

  public void testControlPlainCjkIsTokenized() throws IOException {
    List<Tok> toks = tokenize("東京 タワー");
    assertEquals(toks.toString(), 5, toks.size());
    for (Tok t : toks) {
      assertEquals(toks.toString(), "<CJ>", t.type);
    }
    assertContentKept("東京 タワー", "東京タワー");
  }

  public void testControlLatinAndHangulInsideMarkupAreKept() throws IOException {
    assertContentKept("[[Tokyo]]", "Tokyo");
    assertContentKept("[[서울]]", "서울");
    assertContentKept("[[Category:Seoul]]", "Seoul");
    assertContentKept("== 서울 ==", "서울");
    assertContentKept("'''Seoul'''", "Seoul");
    assertContentKept("''서울''", "서울");
    assertContentKept("<ref>Seoul</ref>", "Seoul");
    assertContentKept("{{Seoul}}", "Seoul");
    assertContentKept("[http://example.com Seoul]", "Seoul");
    assertContentKept("''abc def''", "abcdef");
    assertContentKept("'''''Seoul'''''", "Seoul");
    assertContentKept("=== Seoul ===", "Seoul");
  }

  public void testControlFollowingWordAfterLatinItalicsIsPlain() throws IOException {
    List<Tok> toks = tokenize("a ''bc'' d");
    assertEquals(toks.toString(), "d", toks.get(toks.size() - 1).term);
    assertEquals(toks.toString(), "<ALPHANUM>", toks.get(toks.size() - 1).type);
  }

  // ---- the claim ----

  public void testHanInsideInternalLink() throws IOException {
    assertContentKept("[[東京]]", "東京");
  }

  public void testHanInsideInternalLinkMixedWithLatin() throws IOException {
    // Latin part survives, Han part is the question
    assertContentKept("[[Tokyo 東京]]", "Tokyo東京");
  }

  public void testHanInsideCategory() throws IOException {
    assertContentKept("[[Category:中文]]", "中文");
  }

  public void testHanInsideHeading() throws IOException {
    assertContentKept("== 歴史 ==", "歴史");
  }

  public void testHanInsideBold() throws IOException {
    assertContentKept("'''北京'''", "北京");
  }

  public void testHiraganaInsideItalics() throws IOException {
    assertContentKept("''ひらがな''", "ひらがな");
  }

  public void testKatakanaInsideCitation() throws IOException {
    assertContentKept("<ref>カタカナ</ref>", "カタカナ");
  }

  public void testHanInsideDoubleBrace() throws IOException {
    assertContentKept("{{漢字}}", "漢字");
  }

  public void testHanInsideExternalLinkText() throws IOException {
    assertContentKept("[http://example.com 日本語]", "日本語");
  }

  public void testHanAfterLatinInsideItalics() throws IOException {
    // reaches the STRING state (after the first italic word)
    assertContentKept("''abc 日本''", "abc日本");
  }

  public void testHanInsideBoldItalics() throws IOException {
    assertContentKept("'''''日本'''''", "日本");
  }

  public void testHanInsideSubHeading() throws IOException {
    assertContentKept("=== 日本 ===", "日本");
  }

  /** Secondary effect: the closing '' after CJ-only italics is read as an opening bold. */
  public void testFollowingWordAfterCjkItalicsIsPlain() throws IOException {
    List<Tok> toks = tokenize("a ''日本'' d");
    assertEquals(toks.toString(), "d", toks.get(toks.size() - 1).term);
    assertEquals(toks.toString(), "<ALPHANUM>", toks.get(toks.size() - 1).type);
  }

  /** End to end: the linked Han word is not searchable, while the plain one is. */
  public void testIndexedHanLinkTextIsSearchable() throws IOException {
    Analyzer a =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            return new TokenStreamComponents(new WikipediaTokenizer());
          }
        };
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
        Document plain = new Document();
        plain.add(new TextField("body", "東京", Field.Store.NO));
        w.addDocument(plain);
        Document linked = new Document();
        linked.add(new TextField("body", "[[東京]]", Field.Store.NO));
        w.addDocument(linked);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        int hits = s.count(new TermQuery(new Term("body", "東")));
        assertEquals("docs matching body:東 (plain '東京' and linked '[[東京]]')", 2, hits);
      }
    }
    a.close();
  }
}
