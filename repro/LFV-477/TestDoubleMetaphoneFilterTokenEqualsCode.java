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
package org.apache.lucene.analysis.phonetic;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.commons.codec.language.DoubleMetaphone;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * A token whose text equals its own primary Double Metaphone code (upper-case "JR", "JFK", "SMS",
 * "J") should still get its alternate code, as the same word in lower case does.
 */
public class TestDoubleMetaphoneFilterTokenEqualsCode extends BaseTokenStreamTestCase {

  /** word, primary code, alternate code (maxCodeLength 4). */
  private static final String[][] WORDS = {
    {"JR", "JR", "AR"}, {"JFK", "JFK", "AFK"}, {"SMS", "SMS", "XMS"}, {"J", "J", "A"},
  };

  /** "TERM/positionIncrement" of every token the filter emits for one whitespace token. */
  private static List<String> emit(String text, boolean inject) throws IOException {
    List<String> out = new ArrayList<>();
    try (TokenStream ts = new DoubleMetaphoneFilter(whitespaceMockTokenizer(text), 4, inject)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      PositionIncrementAttribute posInc = ts.addAttribute(PositionIncrementAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        out.add(term + "/" + posInc.getPositionIncrement());
      }
      ts.end();
    }
    return out;
  }

  /** Controls, which pass on stock Lucene. */
  public void testControls() throws IOException {
    DoubleMetaphone encoder = new DoubleMetaphone();
    encoder.setMaxCodeLen(4);
    for (String[] w : WORDS) {
      String lower = w[0].toLowerCase(Locale.ROOT);
      // the encoder ignores letter case, and the upper-case token equals its own primary code
      assertEquals(w[1], encoder.doubleMetaphone(w[0]));
      assertEquals(w[2], encoder.doubleMetaphone(w[0], true));
      assertEquals(w[1], encoder.doubleMetaphone(lower));
      assertEquals(w[2], encoder.doubleMetaphone(lower, true));
      // the lower-case word gets both codes, in both modes
      assertEquals(List.of(w[1] + "/1", w[2] + "/0"), emit(lower, false));
      assertEquals(List.of(lower + "/1", w[1] + "/0", w[2] + "/0"), emit(lower, true));
    }
  }

  /** The upper-case word keeps its text as the primary code and should get the alternate too. */
  public void testTokenEqualToPrimaryCodeKeepsAlternateCode() throws IOException {
    List<String> wrong = new ArrayList<>();
    for (String[] w : WORDS) {
      for (boolean inject : new boolean[] {false, true}) {
        List<String> expected = List.of(w[1] + "/1", w[2] + "/0");
        List<String> got = emit(w[0], inject);
        if (got.equals(expected) == false) {
          wrong.add("inject=" + inject + " " + w[0] + " -> " + got + ", expected " + expected);
        }
      }
    }
    assertTrue(String.join("\n", wrong), wrong.isEmpty());
  }

  /**
   * The factory's documented chain (whitespace tokenizer, then doubleMetaphone with its defaults
   * inject=true, maxCodeLength=4; no lower-casing): a search on the alternate code AR should find
   * both spellings.
   */
  public void testSearchOnAlternateCodeFindsBothSpellings() throws IOException {
    try (Analyzer a =
            CustomAnalyzer.builder()
                .withTokenizer("whitespace")
                .addTokenFilter("doubleMetaphone")
                .build();
        Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(a))) {
        for (String text : new String[] {"jr", "JR"}) {
          Document doc = new Document();
          doc.add(new TextField("body", text, Field.Store.NO));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        assertEquals("hits for JR", 2, s.count(new TermQuery(new Term("body", "JR"))));
        assertEquals("hits for AR", 2, s.count(new TermQuery(new Term("body", "AR"))));
      }
    }
  }
}
