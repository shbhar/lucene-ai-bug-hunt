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
package org.apache.lucene.analysis.th;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.StringMockResourceLoader;
import org.apache.lucene.util.QueryBuilder;

/**
 * A user-dictionary word that ends inside a Thai character cluster (right before a combining vowel
 * or tone mark) must not make characters disappear from the token stream.
 */
public class TestThaiDictionaryWordInsideCluster extends BaseTokenStreamTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    assumeTrue(
        "JRE does not support Thai dictionary-based BreakIterator", ThaiTokenizer.DBBI_AVAILABLE);
  }

  private static Analyzer analyzer(String... words) {
    CharArraySet dict = words.length == 0 ? null : new CharArraySet(List.of(words), false);
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        return new TokenStreamComponents(new ThaiTokenizer(dict));
      }
    };
  }

  /** Every character of an all-Thai text must lie inside some token. */
  private static void assertNoCharacterLost(Analyzer a, String text) throws IOException {
    boolean[] covered = new boolean[text.length()];
    List<String> tokens = new ArrayList<>();
    try (TokenStream ts = a.tokenStream("f", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      OffsetAttribute off = ts.addAttribute(OffsetAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        tokens.add(term + "[" + off.startOffset() + "," + off.endOffset() + ")");
        for (int i = off.startOffset(); i < off.endOffset(); i++) {
          covered[i] = true;
        }
      }
      ts.end();
    }
    for (int i = 0; i < text.length(); i++) {
      String ch = String.format(Locale.ROOT, "U+%04X", (int) text.charAt(i));
      assertTrue(
          "character " + i + " (" + ch + ") of " + text + " is in no token: " + tokens, covered[i]);
    }
  }

  /** {แม่น} ("accurate") ends right before the tone mark of แม่น้ำ ("river"). */
  public void testRiver() throws IOException {
    try (Analyzer a = analyzer("แม่น")) {
      assertNoCharacterLost(a, "แม่น้ำ");
    }
  }

  /** {ที} ends right before the tone mark of the everyday word ที่ in ที่นี่ ("here"). */
  public void testHere() throws IOException {
    try (Analyzer a = analyzer("ที")) {
      assertNoCharacterLost(a, "ที่นี่");
    }
  }

  /** The documented factory parameter: dictionary {ผล} ("result") on ผลิตภัณฑ์ ("product"). */
  public void testFactoryProduct() throws IOException {
    Map<String, String> args = new HashMap<>();
    args.put("dictionary", "custom_words.txt");
    ThaiTokenizerFactory factory = new ThaiTokenizerFactory(args);
    factory.inform(new StringMockResourceLoader("ผล\n"));
    Analyzer a =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            return new TokenStreamComponents(factory.create());
          }
        };
    try (a) {
      assertNoCharacterLost(a, "ผลิตภัณฑ์");
    }
  }

  /** End to end: a search for แม่น้ำ ("river") must not find a document that only says แม่น. */
  public void testSearchForRiverFindsOnlyTheRiver() throws IOException {
    try (Analyzer a = analyzer("แม่น");
        Directory dir = newDirectory()) {
      RandomIndexWriter w = new RandomIndexWriter(random(), dir, a);
      for (String text : new String[] {"แม่น้ำ", "ยิงแม่นมาก"}) { // "river", "shoots accurately"
        Document doc = new Document();
        doc.add(new TextField("f", text, Field.Store.NO));
        w.addDocument(doc);
      }
      try (IndexReader r = w.getReader()) {
        w.close();
        Query q = new QueryBuilder(a).createBooleanQuery("f", "แม่น้ำ");
        assertEquals(q.toString(), 1, newSearcher(r).count(q));
      }
    }
  }

  /** Controls (pass on stock): no dictionary, and a dictionary word that ends between clusters. */
  public void testControls() throws IOException {
    try (Analyzer a = analyzer()) {
      assertAnalyzesTo(a, "แม่น้ำ", new String[] {"แม่น้ำ"});
    }
    try (Analyzer a = analyzer("แม่")) {
      assertAnalyzesTo(
          a, "แม่น้ำ", new String[] {"แม่", "น้ำ"}, new int[] {0, 3}, new int[] {3, 6});
    }
  }
}
