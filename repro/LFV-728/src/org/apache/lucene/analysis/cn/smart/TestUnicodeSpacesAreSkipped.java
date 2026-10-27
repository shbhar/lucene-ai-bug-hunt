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

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.QueryBuilder;

/**
 * Utility.SPACES documents "Space-like characters that need to be skipped" and CharType.SPACE_LIKE "Characters
 * that act as a space", but only ' ', '\t', '\r', '\n' and U+3000 are skipped. NO-BREAK SPACE (U+00A0) falls
 * into the 0x0021-0x00BB punctuation range (removed, leaving a position gap), and THIN SPACE (U+2009) and the other
 * U+2000-U+200A spaces are emitted as one-character whitespace tokens.
 */
public class TestUnicodeSpacesAreSkipped extends LuceneTestCase {

  static List<String> terms(Analyzer a, String text) throws Exception {
    List<String> out = new ArrayList<>();
    try (TokenStream ts = a.tokenStream("f", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      PositionIncrementAttribute posInc = ts.addAttribute(PositionIncrementAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        out.add("[" + term + "]" + (posInc.getPositionIncrement() != 1 ? "(+" + posInc.getPositionIncrement() + ")" : ""));
      }
      ts.end();
    }
    return out;
  }

  /**
   * NO-BREAK SPACE, EN SPACE, EM SPACE and THIN SPACE give the same tokens and positions as an ordinary space
   * (U+202F NARROW NO-BREAK SPACE is left out: other analyzers may keep it inside a word).
   */
  public void testSpaceSeparatorsActLikeSpace() throws Exception {
    List<String> bad = new ArrayList<>();
    try (Analyzer a = new SmartChineseAnalyzer()) {
      List<String> expected = terms(a, "hello world");
      for (char sp : new char[] {'\u00A0', '\u2002', '\u2003', '\u2009'}) {
        List<String> t = terms(a, "hello" + sp + "world");
        if (t.equals(expected) == false) {
          bad.add(String.format(java.util.Locale.ROOT, "U+%04X -> %s", (int) sp, t));
        }
      }
      assertTrue("expected " + expected + " for every space separator, got: " + bad, bad.isEmpty());
    }
  }

  /** A phrase query typed with an ordinary space misses documents that contain a no-break or thin space. */
  public void testPhraseAcrossNoBreakAndThinSpace() throws Exception {
    try (Analyzer a = new SmartChineseAnalyzer();
        Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
        for (String body : new String[] {"hello world", "hello\u00A0world", "hello\u2009world"}) {
          Document d = new Document();
          d.add(new TextField("body", body, Field.Store.YES));
          w.addDocument(d);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        Query q = new QueryBuilder(a).createPhraseQuery("body", "hello world");
        assertEquals("query " + q + " should match all three documents", 3, new IndexSearcher(r).count(q));
      }
    }
  }
}
