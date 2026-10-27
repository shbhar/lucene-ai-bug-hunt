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
package org.apache.lucene.analysis.util;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.cn.smart.HMMChineseTokenizer;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.analysis.th.ThaiTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
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
 * SegmentingTokenizerBase.refill() uses the whole buffer when it finds no safe end (no line break,
 * and for ThaiTokenizer no whitespace), even if the buffer ends in the middle of a surrogate pair.
 * The supplementary character at the buffer boundary is then split across two buffers.
 */
public class TestSegmentingTokenizerBaseSurrogateSplit extends BaseTokenStreamTestCase {

  /** U+20000, a CJK Unified Ideographs Extension B letter (one supplementary code point). */
  private static final String SUPPLEMENTARY = new String(Character.toChars(0x20000));

  private static String repeat(char c, int n) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < n; i++) sb.append(c);
    return sb.toString();
  }

  private static List<String> terms(Tokenizer t, String text, boolean[] covered)
      throws IOException {
    t.setReader(new StringReader(text));
    CharTermAttribute term = t.addAttribute(CharTermAttribute.class);
    OffsetAttribute off = t.addAttribute(OffsetAttribute.class);
    List<String> out = new ArrayList<>();
    t.reset();
    while (t.incrementToken()) {
      out.add(term.toString());
      for (int i = off.startOffset(); i < off.endOffset(); i++) covered[i] = true;
    }
    t.end();
    t.close();
    return out;
  }

  private static boolean hasUnpairedSurrogate(String s) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (Character.isHighSurrogate(c)) {
        if (i + 1 == s.length() || !Character.isLowSurrogate(s.charAt(i + 1))) return true;
        i++;
      } else if (Character.isLowSurrogate(c)) {
        return true;
      }
    }
    return false;
  }

  /** HMMChineseTokenizer (default buffer of 1024 chars): no term may contain half a character. */
  public void testHMMChineseTokenizerAtBufferBoundary() throws IOException {
    // 1023 BMP ideographs, then the supplementary ideograph occupies chars 1023 and 1024
    String text = repeat('中', 1023) + SUPPLEMENTARY + "中文";
    boolean[] covered = new boolean[text.length()];
    for (String term : terms(new HMMChineseTokenizer(), text, covered)) {
      assertFalse(
          "term with an unpaired surrogate: " + String.format(java.util.Locale.ROOT, "U+%04X", (int) term.charAt(0)),
          hasUnpairedSurrogate(term));
    }
  }

  /** Index level, SmartChineseAnalyzer as shipped: the character cannot be found afterwards. */
  public void testSmartChineseAnalyzerSearch() throws IOException {
    String text = repeat('中', 1023) + SUPPLEMENTARY + "中文";
    try (Analyzer a = new SmartChineseAnalyzer();
        Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
        Document doc = new Document();
        doc.add(new TextField("f", text, Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        assertEquals(
            "documents containing U+20000", 1, s.count(new TermQuery(new Term("f", SUPPLEMENTARY))));
      }
    }
  }

  /** Control: the same character one position earlier is a normal token. */
  public void testHMMChineseTokenizerControl() throws IOException {
    String text = repeat('中', 1000) + SUPPLEMENTARY + "中文";
    boolean[] covered = new boolean[text.length()];
    List<String> terms = terms(new HMMChineseTokenizer(), text, covered);
    assertTrue(terms.contains(SUPPLEMENTARY));
  }

  /** ThaiTokenizer (default buffer of 1024 chars): the supplementary letter must not be dropped. */
  public void testThaiTokenizerAtBufferBoundary() throws IOException {
    assumeTrue("needs the Thai BreakIterator", ThaiTokenizer.DBBI_AVAILABLE);
    String text = repeat('ก', 1023) + SUPPLEMENTARY + "ภาษาไทย";
    boolean[] covered = new boolean[text.length()];
    List<String> terms = terms(new ThaiTokenizer(), text, covered);
    assertTrue(
        "supplementary letter at chars 1023-1024 is in no token; terms around it: "
            + terms.subList(Math.max(0, terms.size() - 3), terms.size()),
        covered[1023] && covered[1024]);
  }
}
