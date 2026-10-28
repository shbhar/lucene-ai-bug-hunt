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

import static org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter.GENERATE_WORD_PARTS;

import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.analysis.MockTokenizer;

/**
 * WordDelimiterGraphFilter accumulates the position increment of a swallowed all-delimiter token
 * (accumPosInc, "so that we preserve incoming holes") and adds it to the next emitted token, but it
 * does not override end(), so when the swallowed tokens are the last ones of the stream the hole
 * (and any incoming hole carried by them) is dropped instead of being reported in the final
 * position increment (TokenStream#end javadoc; FilteringTokenFilter#end, LUCENE-3849).
 */
public class TestWdgfTrailingHole extends BaseTokenStreamTestCase {

  private static final CharArraySet STOP = new CharArraySet(List.of("the"), false);

  private static Analyzer analyzer(boolean stop, boolean wdgf) {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer t = new MockTokenizer(MockTokenizer.WHITESPACE, false);
        TokenStream ts = t;
        if (stop) {
          ts = new StopFilter(ts, STOP);
        }
        if (wdgf) {
          ts = new WordDelimiterGraphFilter(ts, GENERATE_WORD_PARTS, null);
        }
        return new TokenStreamComponents(t, ts);
      }
    };
  }

  private static void assertFinalPosInc(Analyzer a, String text, String[] terms, int finalPosInc)
      throws Exception {
    try (TokenStream ts = a.tokenStream("f", text)) {
      assertTokenStreamContents(
          ts, terms, null, null, null, null, null, text.length(), finalPosInc, null, false, null);
    }
  }

  /** Control: a trailing stop word is reported as a final position increment of 1 (passes). */
  public void testControlStopFilter() throws Exception {
    try (Analyzer a = analyzer(true, false)) {
      assertFinalPosInc(a, "foo the", new String[] {"foo"}, 1);
    }
  }

  /** A trailing all-delimiter token is swallowed like a stop word: final increment must be 1. */
  public void testTrailingDelimiterToken() throws Exception {
    try (Analyzer a = analyzer(false, true)) {
      assertFinalPosInc(a, "foo --", new String[] {"foo"}, 1);
    }
  }

  /** The incoming hole of the stop word is lost as well: final increment must be 2 (0 on main). */
  public void testIncomingHoleLost() throws Exception {
    try (Analyzer a = analyzer(true, true)) {
      assertFinalPosInc(a, "foo the --", new String[] {"foo"}, 2);
    }
  }

  /**
   * Consequence for multi-valued fields: with the StopFilter the second value starts after the hole,
   * with WordDelimiterGraphFilter it does not, so "foo bar" matches across the values.
   */
  public void testMultiValuedFieldPositions() throws Exception {
    for (boolean useWdgf : new boolean[] {false, true}) {
      try (Analyzer a = analyzer(true, useWdgf);
          Directory dir = newDirectory()) {
        try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(a))) {
          Document doc = new Document();
          doc.add(new Field("body", useWdgf ? "foo --" : "foo the", TextField.TYPE_NOT_STORED));
          doc.add(new Field("body", "bar", TextField.TYPE_NOT_STORED));
          w.addDocument(doc);
        }
        try (DirectoryReader r = DirectoryReader.open(dir)) {
          IndexSearcher s = newSearcher(r);
          assertEquals(
              "wdgf=" + useWdgf, 0, s.count(new PhraseQuery("body", "foo", "bar")));
        }
      }
    }
  }
}
