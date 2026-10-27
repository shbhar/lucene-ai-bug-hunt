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
package org.apache.lucene.analysis.ko;

import java.io.IOException;
import java.io.StringReader;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ko.dict.UserDictionary;
import org.apache.lucene.analysis.ko.tokenattributes.PartOfSpeechAttribute;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

/**
 * Prints, for one-line user dictionaries that differ only in how the line ends, the tagged tokens
 * (KoreanTokenizer, DecompoundMode.NONE) and the KoreanAnalyzer terms (defaults) of a few texts.
 * Public API only (8.2 and later).
 */
public class Lfv654Probe {

  static UserDictionary dict(String line) throws IOException {
    return UserDictionary.open(new StringReader(line));
  }

  static String tagged(String line, String text) throws IOException {
    StringBuilder sb = new StringBuilder();
    try (KoreanTokenizer tok =
        new KoreanTokenizer(
            TokenStream.DEFAULT_TOKEN_ATTRIBUTE_FACTORY,
            dict(line),
            KoreanTokenizer.DecompoundMode.NONE,
            false,
            true)) {
      CharTermAttribute term = tok.addAttribute(CharTermAttribute.class);
      PartOfSpeechAttribute pos = tok.addAttribute(PartOfSpeechAttribute.class);
      tok.setReader(new StringReader(text));
      tok.reset();
      while (tok.incrementToken()) {
        sb.append(term).append('/').append(pos.getLeftPOS()).append(' ');
      }
      tok.end();
    }
    return sb.toString().trim();
  }

  static String terms(String line, String text) throws IOException {
    StringBuilder sb = new StringBuilder();
    try (Analyzer a =
            new KoreanAnalyzer(
                dict(line),
                KoreanTokenizer.DEFAULT_DECOMPOUND,
                KoreanPartOfSpeechStopFilter.DEFAULT_STOP_TAGS,
                false);
        TokenStream ts = a.tokenStream("f", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        sb.append('[').append(term).append(']');
      }
      ts.end();
    }
    return sb.toString();
  }

  static String show(String line) {
    return "'" + line.replace("\t", "\\t") + "'";
  }

  static void row(boolean analyzer, String text, String... lines) throws IOException {
    StringBuilder sb = new StringBuilder((analyzer ? "terms  " : "tagged ") + text + " :");
    String first = null;
    boolean differ = false;
    for (String line : lines) {
      String out = analyzer ? terms(line, text) : tagged(line, text);
      if (first == null) {
        first = out;
      } else if (!first.equals(out)) {
        differ = true;
      }
      sb.append("  ").append(show(line)).append(" -> ").append(out).append(" ;");
    }
    System.out.println((differ ? "DIFF " : "same ") + sb);
  }

  public static void main(String[] args) throws IOException {
    row(false, "학교이", "학교", "학교 ", "학교 # memo", "학교# memo");
    row(false, "학교가", "학교", "학교 ", "학교 # memo", "학교# memo");
    row(false, "사람가", "사람", "사람 ", "사람 # memo", "사람# memo");
    row(false, "사람이나", "사람", "사람 ", "사람 # memo", "사람# memo");
    row(false, "학교가방을 샀다", "학교", "학교 ", "학교 # memo", "학교# memo");
    row(false, "세종시가", "세종시 세종 시", "세종시 세종");
    row(true, "학교가방을 샀다", "학교", "학교 ", "학교 # memo", "학교# memo");
    row(true, "사람이나", "사람", "사람 ", "사람 # memo", "사람# memo");
    row(true, "학교이", "학교", "학교 ", "학교 # memo", "학교# memo");
  }
}
