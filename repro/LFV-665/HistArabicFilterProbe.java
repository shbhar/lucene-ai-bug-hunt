/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE
 * file distributed with this work for additional information regarding copyright ownership. The ASF licenses this
 * file to You under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.apache.lucene.analysis.ar;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

/**
 * Public-API history probe for 3.1 and later (CharTermAttribute API, which every release from 3.1 to main has; the
 * tokenizer and ArabicAnalyzer constructors changed across versions, so a one-token TokenStream feeds the public
 * ArabicStemFilter directly). Exits non-zero (AssertionError "BUG") when waw+article keeps its article; a control
 * failure throws IllegalStateException "CONTROL".
 */
public class HistArabicFilterProbe {

  /** Emits one fixed token. */
  static final class OneToken extends TokenStream {
    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private final String text;
    private boolean done;

    OneToken(String text) {
      this.text = text;
    }

    @Override
    public boolean incrementToken() {
      if (done) return false;
      clearAttributes();
      termAtt.setEmpty().append(text);
      done = true;
      return true;
    }

    @Override
    public void reset() {
      done = false;
    }
  }

  static String filter(String w) throws Exception {
    TokenStream ts = new ArabicStemFilter(new OneToken(w));
    CharTermAttribute t = ts.addAttribute(CharTermAttribute.class);
    ts.reset();
    if (!ts.incrementToken()) throw new IllegalStateException("no token");
    String out = t.toString();
    ts.end();
    ts.close();
    return out;
  }

  public static void main(String[] args) throws Exception {
    String ctl1 = filter("بالكتاب");
    String ctl2 = filter("للطلاب");
    String ctl3 = filter("والكتاب");
    System.out.println("control filter bal-kitab -> " + ctl1 + " ; lil-tullab -> " + ctl2 + " ; wal-kitab -> " + ctl3);
    if (!ctl1.equals("كتاب") || !ctl2.equals("طلاب") || !ctl3.equals("كتاب"))
      throw new IllegalStateException("CONTROL failed");
    String[][] bug = {
      {"وبالكتاب", "كتاب"}, {"وللطلاب", "طلاب"}, {"وكالبحر", "بحر"}, {"وفالكتاب", "كتاب"},
    };
    StringBuilder bad = new StringBuilder();
    int n = 0;
    for (String[] b : bug) {
      String got = filter(b[0]);
      boolean ok = got.equals(b[1]);
      System.out.println("filter " + b[0] + " -> " + got + " (light10 " + b[1] + ") " + (ok ? "ok" : "MISMATCH"));
      if (!ok) {
        n++;
        bad.append(' ').append(b[0]).append("->").append(got);
      }
    }
    if (n > 0) throw new AssertionError("BUG waw+article not stripped by ArabicStemFilter (" + n + "/4):" + bad);
    System.out.println("no mismatch");
  }
}
