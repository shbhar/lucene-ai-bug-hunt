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

import java.io.Reader;
import java.io.StringReader;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.DecimalDigitFilter;
import org.apache.lucene.analysis.th.ThaiAnalyzer;
import org.apache.lucene.analysis.th.ThaiCharFilter;
import org.apache.lucene.analysis.th.ThaiNormalizationFilter;
import org.apache.lucene.analysis.th.ThaiRepeatFilter;
import org.apache.lucene.analysis.th.ThaiTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;

/**
 * Prints, for each (dictionary, text) pair: the tokens without a dictionary, the tokens with it
 * (term[start,end)), the characters that lie in no token, and whether the word and character
 * BreakIterators have a boundary where the dictionary word ends. Then the terms of the stock
 * ThaiAnalyzer chain (ThaiCharFilter, LowerCaseFilter, DecimalDigitFilter, ThaiNormalizationFilter,
 * ThaiRepeatFilter, StopFilter) built around a ThaiTokenizer with the dictionary. Public API only.
 */
public class ThaiDictClusterProbe {

  static final String[][] CASES = {
    // dictionary (comma separated), text, note
    {"แม่น", "แม่น้ำ", "river; dict word = accurate"},
    {"แม่น", "แม่น", "accurate (for the collapse)"},
    {"แม่น", "แม่น้ำเจ้าพระยา", "Chao Phraya River"},
    {"แม่น", "แม่น้ำไป", "river + go"},
    {"ที", "ที่นี่", "here"},
    {"ผล", "ผลิต", "produce"},
    {"ผล", "ผลิตภัณฑ์", "product"},
    {"สม", "ผมซื้อสมุดใหม่", "I buy a new notebook"},
    {"คน", "คนึงหา", "to miss someone"},
    {"กล", "กล้ำกลืน", "to swallow (one's pride)"},
    {"เก", "เขาเก่งมาก", "he is very clever"},
    {"แม่", "แม่น้ำ", "CONTROL: word ends between clusters"},
    {"ผล", "ผลไม้", "CONTROL: fruit, word ends between clusters"},
    {"ผล,ผลิต", "ผลิต", "CONTROL: a longer entry covers the word"},
  };

  static List<String> tokens(CharArraySet dict, String text, boolean[] covered) throws Exception {
    Tokenizer t = dict == null ? new ThaiTokenizer() : new ThaiTokenizer(dict);
    t.setReader(new StringReader(text));
    CharTermAttribute term = t.addAttribute(CharTermAttribute.class);
    OffsetAttribute off = t.addAttribute(OffsetAttribute.class);
    List<String> out = new ArrayList<>();
    t.reset();
    while (t.incrementToken()) {
      out.add(term + "[" + off.startOffset() + "," + off.endOffset() + ")");
      if (covered != null) {
        for (int i = off.startOffset(); i < off.endOffset(); i++) covered[i] = true;
      }
    }
    t.end();
    t.close();
    return out;
  }

  /** ThaiAnalyzer's own chain, with a user dictionary passed to the tokenizer. */
  static Analyzer thaiChain(CharArraySet dict) {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer source = new ThaiTokenizer(dict);
        TokenStream result = new LowerCaseFilter(source);
        result = new DecimalDigitFilter(result);
        result = new ThaiNormalizationFilter(result);
        result = new ThaiRepeatFilter(result);
        result = new StopFilter(result, ThaiAnalyzer.getDefaultStopSet());
        return new TokenStreamComponents(source, result);
      }

      @Override
      protected Reader initReader(String fieldName, Reader reader) {
        return new ThaiCharFilter(reader);
      }
    };
  }

  static List<String> terms(Analyzer a, String text) throws Exception {
    List<String> out = new ArrayList<>();
    try (TokenStream ts = a.tokenStream("f", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) out.add(term.toString());
      ts.end();
    }
    return out;
  }

  public static void main(String[] args) throws Exception {
    Locale th = new Locale.Builder().setLanguageTag("th").build();
    System.out.println("DBBI_AVAILABLE=" + ThaiTokenizer.DBBI_AVAILABLE);
    int lostCases = 0;
    for (String[] c : CASES) {
      CharArraySet dict = new CharArraySet(List.of(c[0].split(",")), false);
      String text = c[1];
      boolean[] covered = new boolean[text.length()];
      List<String> with = tokens(dict, text, covered);
      List<String> without = tokens(null, text, null);
      StringBuilder lost = new StringBuilder();
      for (int i = 0; i < text.length(); i++) {
        if (!covered[i]) {
          lost.append(String.format(Locale.ROOT, " %d:U+%04X(type %d)", i, (int) text.charAt(i),
              Character.getType(text.charAt(i))));
        }
      }
      if (lost.length() > 0) lostCases++;
      // where does the first dictionary match end, if the text starts with an entry?
      String bi = "";
      for (String w : c[0].split(",")) {
        if (text.startsWith(w) && w.length() < text.length()) {
          BreakIterator wb = BreakIterator.getWordInstance(th);
          wb.setText(text);
          BreakIterator cb = BreakIterator.getCharacterInstance(th);
          cb.setText(text);
          bi += " at " + w.length() + ": wordBoundary=" + wb.isBoundary(w.length())
              + " charBoundary=" + cb.isBoundary(w.length());
        }
      }
      System.out.println("{" + c[0] + "} " + text + " (" + c[2] + ")");
      System.out.println("   no dict:   " + without);
      System.out.println("   with dict: " + with);
      System.out.println("   lost:     " + (lost.length() == 0 ? " none" : lost));
      if (!bi.isEmpty()) System.out.println("   BI:       " + bi);
    }
    System.out.println("cases with lost characters: " + lostCases + " of " + CASES.length);
    System.out.println("== ThaiAnalyzer chain with the dictionary in the tokenizer (terms)");
    try (Analyzer stock = new ThaiAnalyzer()) {
      for (String[] c : CASES) {
        if (c[2].startsWith("CONTROL")) continue;
        try (Analyzer chain = thaiChain(new CharArraySet(List.of(c[0].split(",")), false))) {
          System.out.println("{" + c[0] + "} " + c[1] + ": ThaiAnalyzer " + terms(stock, c[1])
              + " | chain with dict " + terms(chain, c[1]));
        }
      }
    }
  }
}
