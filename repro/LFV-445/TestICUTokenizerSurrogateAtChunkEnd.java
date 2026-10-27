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
package org.apache.lucene.analysis.icu.segmentation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * ICUTokenizer reads its input 4096 chars at a time. A chunk without whitespace is used whole, so
 * when its last char is the first half (high surrogate) of a supplementary character such as an
 * emoji or a CJK Extension B ideograph, that character ends up in no token. One char earlier or
 * later it is a token.
 */
public class TestICUTokenizerSurrogateAtChunkEnd extends BaseTokenStreamTestCase {

  private static final String EXT_B = new String(Character.toChars(0x20000)); // CJK Ext. B
  private static final String EMOJI = new String(Character.toChars(0x1F600)); // grinning face

  private record Tok(String term, int start, int end) {
    @Override
    public String toString() {
      return term + "[" + start + "," + end + ")";
    }
  }

  private static Analyzer icuTokenizer() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        return new TokenStreamComponents(new ICUTokenizer());
      }
    };
  }

  /** 4200 chars of Chinese without whitespace, with {@code probe} starting at char {@code pos}. */
  private static String text(String probe, int pos) {
    StringBuilder sb = new StringBuilder();
    while (sb.length() < pos) {
      sb.append("中文");
    }
    sb.setLength(pos);
    sb.append(probe);
    while (sb.length() < Math.max(4200, pos + 100)) {
      sb.append("中文");
    }
    return sb.toString();
  }

  private static List<Tok> tokens(Analyzer a, String text) throws IOException {
    List<Tok> out = new ArrayList<>();
    try (TokenStream ts = a.tokenStream("body", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      OffsetAttribute offset = ts.addAttribute(OffsetAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        out.add(new Tok(term.toString(), offset.startOffset(), offset.endOffset()));
      }
      ts.end();
    }
    return out;
  }

  /** The supplementary character at {@code pos} must be the token probe[pos, pos + 2). */
  private static void assertIsAToken(Analyzer a, String probe, int pos) throws IOException {
    List<Tok> tokens = tokens(a, text(probe, pos));
    if (tokens.contains(new Tok(probe, pos, pos + 2)) == false) {
      List<Tok> near = new ArrayList<>();
      for (Tok t : tokens) {
        if (t.end() >= pos - 2 && t.start() <= pos + 4) {
          near.add(t);
        }
      }
      fail(
          String.format(
              Locale.ROOT,
              "U+%X at char %d is in no token; tokens near it: %s",
              probe.codePointAt(0),
              pos,
              near));
    }
  }

  /** Controls: one char before or after the chunk end, both characters are tokens. */
  public void testOneCharAwayFromTheChunkEnd() throws IOException {
    try (Analyzer a = icuTokenizer()) {
      for (int pos : new int[] {10, 4094, 4096}) {
        assertIsAToken(a, EXT_B, pos);
        assertIsAToken(a, EMOJI, pos);
      }
    }
  }

  /** Char 4095 is the last char of the first 4096-char chunk. */
  public void testIdeographAtChunkEnd() throws IOException {
    try (Analyzer a = icuTokenizer()) {
      assertIsAToken(a, EXT_B, 4095);
    }
  }

  public void testEmojiAtChunkEnd() throws IOException {
    try (Analyzer a = icuTokenizer()) {
      assertIsAToken(a, EMOJI, 4095);
    }
  }

  /** The SPI factory "icu" with default parameters; char 8191 ends the second chunk. */
  public void testFactoryAtSecondChunkEnd() throws IOException {
    try (Analyzer a = CustomAnalyzer.builder().withTokenizer("icu").build()) {
      assertIsAToken(a, EXT_B, 8191);
    }
  }

  /** End to end: a TermQuery for the ideograph must find all three documents. */
  public void testTermQueryFindsEveryDocument() throws IOException {
    try (Directory dir = newDirectory();
        Analyzer a = icuTokenizer()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(a))) {
        for (int pos : new int[] {4094, 4095, 4096}) {
          Document doc = new Document();
          doc.add(new TextField("body", text(EXT_B, pos), Field.Store.NO));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        int hits = newSearcher(r).count(new TermQuery(new Term("body", EXT_B)));
        assertEquals("documents found by TermQuery(U+20000)", 3, hits);
      }
    }
  }
}
