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
package org.apache.lucene.analysis.ja;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionLengthAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.util.QueryBuilder;

/**
 * With nBestCost > 0, JapaneseTokenizer outputs the tokens of every Viterbi path within the cost
 * bound and sets each token's position length from the token boundaries of the batch. With
 * discardPunctuation=true (the default) a discarded punctuation token still contributes its
 * boundary to that computation, while position increments only count the start offsets of the
 * tokens actually emitted. A dictionary word with an inner ・ (アイワ・コーポ) whose split
 * alternative アイワ / ・ / コーポ is also on an n-best path then gets position length 3 instead of
 * 2: it ends on the position after the next word, so its path skips that word.
 */
public class TestNBestPositionLengthDiscardedPunctuation extends BaseTokenStreamTestCase {

  private static Analyzer analyzer(boolean discardPunctuation, JapaneseTokenizer.Mode mode) {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        JapaneseTokenizer tokenizer =
            new JapaneseTokenizer(newAttributeFactory(), null, discardPunctuation, true, mode);
        tokenizer.setNBestCost(2000);
        return new TokenStreamComponents(tokenizer, tokenizer);
      }
    };
  }

  /**
   * Returns a description of the first token whose path does not continue at the token that starts
   * where it ends, or null if every path is linked.
   */
  private static String firstBrokenLink(Analyzer a, String text) throws IOException {
    List<String> terms = new ArrayList<>();
    List<int[]> toks = new ArrayList<>(); // start, end, startPos, endPos
    try (TokenStream ts = a.tokenStream("f", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      OffsetAttribute off = ts.addAttribute(OffsetAttribute.class);
      PositionIncrementAttribute inc = ts.addAttribute(PositionIncrementAttribute.class);
      PositionLengthAttribute len = ts.addAttribute(PositionLengthAttribute.class);
      ts.reset();
      int pos = -1;
      while (ts.incrementToken()) {
        pos += inc.getPositionIncrement();
        terms.add(term.toString());
        toks.add(new int[] {off.startOffset(), off.endOffset(), pos, pos + len.getPositionLength()});
      }
      ts.end();
    }
    Map<Integer, Integer> posOfStartOffset = new HashMap<>();
    for (int[] t : toks) {
      posOfStartOffset.putIfAbsent(t[0], t[2]);
    }
    for (int i = 0; i < toks.size(); i++) {
      int[] t = toks.get(i);
      Integer next = posOfStartOffset.get(t[1]);
      if (next != null && next != t[3]) {
        return terms.get(i) + " [" + t[0] + "-" + t[1] + "] ends at position " + t[3]
            + " but the token starting at offset " + t[1] + " is at position " + next;
      }
    }
    return null;
  }

  public void testControlKeepPunctuation() throws IOException {
    for (JapaneseTokenizer.Mode mode : JapaneseTokenizer.Mode.values()) {
      try (Analyzer a = analyzer(false, mode)) {
        assertNull(mode.toString(), firstBrokenLink(a, "アイワ・コーポ東京支店"));
      }
    }
  }

  public void testPathSkipsNextWord() throws IOException {
    List<String> broken = new ArrayList<>();
    for (JapaneseTokenizer.Mode mode : JapaneseTokenizer.Mode.values()) {
      try (Analyzer a = analyzer(true, mode)) {
        String b = firstBrokenLink(a, "アイワ・コーポ東京支店");
        if (b != null) {
          broken.add(mode + ": " + b);
        }
      }
    }
    assertTrue(broken.toString(), broken.isEmpty());
  }

  /** A graph-aware query for the Tokyo branch also matches the Osaka branch. */
  public void testGraphQueryMatchesDocumentWithoutRequiredTerm() throws IOException {
    try (Analyzer a = analyzer(true, JapaneseTokenizer.Mode.SEARCH);
        Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
        for (String[] d : new String[][] {{"tokyo", "アイワ・コーポ東京支店"}, {"osaka", "アイワ・コーポの大阪支店"}}) {
          Document doc = new Document();
          doc.add(new StringField("id", d[0], Field.Store.YES));
          doc.add(new TextField("body", d[1], Field.Store.NO));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        Query q = new QueryBuilder(a).createBooleanQuery("body", "アイワ・コーポ東京支店", BooleanClause.Occur.MUST);
        List<String> ids = new ArrayList<>();
        for (ScoreDoc sd : s.search(q, 10).scoreDocs) {
          ids.add(s.storedFields().document(sd.doc).get("id"));
        }
        assertEquals("query " + q, List.of("tokyo"), ids);
      }
    }
  }
}
