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
package org.apache.lucene.analysis.ko.dict;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ko.KoreanAnalyzer;
import org.apache.lucene.analysis.ko.KoreanPartOfSpeechStopFilter;
import org.apache.lucene.analysis.ko.KoreanTokenizer;
import org.apache.lucene.analysis.ko.POS;
import org.apache.lucene.analysis.ko.tokenattributes.PartOfSpeechAttribute;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.util.IntsRef;
import org.apache.lucene.util.fst.FST;

/**
 * Token dump for the brief (10.0 and later): the context ids of user and bundled nouns, the tags
 * and KoreanAnalyzer terms of a few noun + particle texts with and without a one-line user
 * dictionary, and the cost of each reading of the particles 가 and 이 from each NNG right context.
 */
public class Lfv658Probe {

  static UserDictionary dict(String noun) throws IOException {
    return UserDictionary.open(new StringReader(noun));
  }

  static String userIds(String noun) throws IOException {
    UserDictionary d = dict(noun);
    List<Integer> ids = d.lookup(noun.toCharArray(), 0, noun.length());
    UserMorphData m = d.getMorphAttributes();
    return m.getLeftId(ids.get(0)) + "/" + m.getRightId(ids.get(0));
  }

  /** word ids of every bundled entry whose surface form is exactly {@code s}. */
  static List<Integer> bundled(String s) throws IOException {
    TokenInfoDictionary dict = TokenInfoDictionary.getInstance();
    TokenInfoFST fst = dict.getFST();
    FST.BytesReader r = fst.getBytesReader();
    FST.Arc<Long> arc = fst.getFirstArc(new FST.Arc<>());
    long out = 0;
    List<Integer> res = new ArrayList<>();
    for (int i = 0; i < s.length(); i++) {
      if (fst.findTargetArc(s.charAt(i), arc, arc, i == 0, r) == null) {
        return res;
      }
      out += arc.output();
    }
    if (!arc.isFinal()) {
      return res;
    }
    out += arc.nextFinalOutput();
    IntsRef ids = new IntsRef();
    dict.lookupWordIds((int) out, ids);
    for (int k = 0; k < ids.length; k++) {
      res.add(ids.ints[ids.offset + k]);
    }
    return res;
  }

  static String pos(TokenInfoMorphData m, int w) {
    String p = m.getLeftPOS(w).name();
    if (m.getRightPOS(w) != m.getLeftPOS(w)) {
      p += "+" + m.getRightPOS(w).name();
    }
    return p;
  }

  static String bundledNngIds(String noun) throws IOException {
    TokenInfoMorphData m = TokenInfoDictionary.getInstance().getMorphAttributes();
    StringBuilder sb = new StringBuilder();
    for (int w : bundled(noun)) {
      if (m.getLeftPOS(w) == POS.Tag.NNG && m.getPOSType(w) == POS.Type.MORPHEME) {
        sb.append(m.getLeftId(w)).append('/').append(m.getRightId(w)).append(' ');
      }
    }
    return sb.length() == 0 ? "(not in the bundled dictionary as a plain NNG)" : sb.toString().trim();
  }

  static String tagged(UserDictionary d, String text) throws IOException {
    StringBuilder sb = new StringBuilder();
    try (KoreanTokenizer t =
        new KoreanTokenizer(
            TokenStream.DEFAULT_TOKEN_ATTRIBUTE_FACTORY,
            d,
            KoreanTokenizer.DecompoundMode.NONE,
            false,
            true)) {
      CharTermAttribute term = t.addAttribute(CharTermAttribute.class);
      PartOfSpeechAttribute p = t.addAttribute(PartOfSpeechAttribute.class);
      t.setReader(new StringReader(text));
      t.reset();
      while (t.incrementToken()) {
        String tag = p.getLeftPOS().name();
        if (p.getRightPOS() != p.getLeftPOS()) {
          tag += "+" + p.getRightPOS().name();
        }
        sb.append(term).append('/').append(tag).append(' ');
      }
      t.end();
    }
    return sb.toString().trim();
  }

  static List<String> terms(UserDictionary d, String text) throws IOException {
    List<String> out = new ArrayList<>();
    try (Analyzer a =
            new KoreanAnalyzer(
                d,
                KoreanTokenizer.DEFAULT_DECOMPOUND,
                KoreanPartOfSpeechStopFilter.DEFAULT_STOP_TAGS,
                false);
        TokenStream ts = a.tokenStream("f", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        out.add(term.toString());
      }
      ts.end();
    }
    return out;
  }

  public static void main(String[] args) throws Exception {
    System.out.println("== context ids (left/right): user entry vs plain NNG entries of the bundled dictionary");
    for (String noun : new String[] {"학교", "나무", "학생", "가방", "루씬", "노리", "깃허브"}) {
      System.out.println(
          "IDS " + noun + " user=" + userIds(noun) + " bundledNNG=" + bundledNngIds(noun));
    }

    System.out.println("== tags (KoreanTokenizer, NONE) and KoreanAnalyzer terms, without | with the user entry");
    String[][] cases = {
      {"학교", "학교가"},
      {"학생", "학생이"},
      {"나무", "나무가"},
      {"가방", "가방이"},
      {"학교", "학교를"},
      {"학생", "학생을"},
      {"학교", "학교가 있다"},
      {"학생", "학생이 있다"},
      {"학교", "학교가 되었다"},
      {"학생", "학생이 중요하다"},
      {"루씬", "루씬이"},
      {"루씬", "루씬이 빠르다"},
      {"노리", "노리가"},
      {"노리", "노리가 있다"},
      {"깃허브", "깃허브가 있다"},
    };
    for (String[] c : cases) {
      UserDictionary d = dict(c[0]);
      String t0 = tagged(null, c[1]);
      String t1 = tagged(d, c[1]);
      List<String> a0 = terms(null, c[1]);
      List<String> a1 = terms(d, c[1]);
      System.out.println(
          (t0.equals(t1) && a0.equals(a1) ? "SAME " : "DIFF ")
              + "["
              + c[1]
              + "] user="
              + c[0]
              + " | tags: "
              + t0
              + " | "
              + t1
              + " | terms: "
              + a0
              + " | "
              + a1);
    }

    System.out.println(
        "== cost of each reading of a particle that ends the text, from noun right context R:"
            + " conn(R, left) + wordCost + conn(right, end-of-text)");
    ConnectionCosts cc = ConnectionCosts.getInstance();
    TokenInfoMorphData m = TokenInfoDictionary.getInstance().getMorphAttributes();
    for (String particle : new String[] {"가", "이"}) {
      for (int r = 3532; r <= 3535; r++) {
        StringBuilder sb = new StringBuilder();
        int best = Integer.MAX_VALUE;
        String bestPos = null;
        for (int w : bundled(particle)) {
          int cost = cc.get(r, m.getLeftId(w)) + m.getWordCost(w) + cc.get(m.getRightId(w), 0);
          sb.append(pos(m, w)).append('=').append(cost).append(' ');
          if (cost < best) {
            best = cost;
            bestPos = pos(m, w);
          }
        }
        System.out.println(
            "COST " + particle + " from R" + r + ": best " + bestPos + " (" + best + ") | all: " + sb);
      }
    }
  }
}
