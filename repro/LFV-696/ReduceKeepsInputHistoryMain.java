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
package stempelhistory;

import java.io.InputStream;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.pl.PolishAnalyzer;
import org.apache.lucene.analysis.stempel.StempelStemmer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.egothor.stemmer.Gener;
import org.egothor.stemmer.Lift;
import org.egothor.stemmer.MultiTrie2;
import org.egothor.stemmer.Optimizer;
import org.egothor.stemmer.Trie;

/**
 * Version-portable form of TestReduceKeepsInput (compiles against every release from 3.1 on; no test framework).
 *
 * <p>A: reduce(new Lift(false)) on a two-letter trie changes what the input trie answers.
 *
 * <p>B: reduce(new Gener()) on a privately loaded copy of the bundled Polish table changes what that copy stems.
 *
 * <p>C: reduce(new Gener()) on PolishAnalyzer.getDefaultTable() changes what a new PolishAnalyzer produces (needs
 * getDefaultTable, looked up by reflection so that the class also runs on releases without it).
 *
 * <p>D (control): reduce(new Optimizer()) on a privately loaded copy of the bundled table leaves that copy unchanged.
 *
 * <p>Exit code 1 if A, B or C shows a changed input; 0 otherwise. Public API only.
 */
public class ReduceKeepsInputHistoryMain {
  private static final String TEXT = "książkami rzekami pracowali";
  private static final String[] WORDS = TEXT.split(" ");

  private static String stems(Trie table) {
    StempelStemmer s = new StempelStemmer(table);
    List<String> out = new ArrayList<String>();
    for (String w : WORDS) {
      out.add(String.valueOf(s.stem(w)));
    }
    return out.toString();
  }

  private static Trie loadBundledCopy() throws Exception {
    InputStream in = PolishAnalyzer.class.getResourceAsStream(PolishAnalyzer.DEFAULT_STEMMER_FILE);
    try {
      return StempelStemmer.load(in);
    } finally {
      in.close();
    }
  }

  /** New PolishAnalyzer: no-arg constructor (5.0+) or the Version constructor (3.x/4.x). */
  private static Analyzer newPolishAnalyzer() throws Exception {
    try {
      return PolishAnalyzer.class.getConstructor().newInstance();
    } catch (NoSuchMethodException e) {
      Class<?> vc = Class.forName("org.apache.lucene.util.Version");
      Object v = vc.getField("LUCENE_CURRENT").get(null);
      return PolishAnalyzer.class.getConstructor(vc).newInstance(v);
    }
  }

  private static String terms(Analyzer a) throws Exception {
    List<String> out = new ArrayList<String>();
    TokenStream ts = a.tokenStream("f", new StringReader(TEXT));
    CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
    ts.reset();
    while (ts.incrementToken()) {
      out.add(term.toString());
    }
    ts.end();
    ts.close();
    return out.toString();
  }

  public static void main(String[] args) throws Exception {
    boolean changed = false;

    // A: Lift on a small trie built through the public API.
    MultiTrie2 trie = new MultiTrie2(true);
    trie.add("aa", "Da");
    String aBefore = String.valueOf(trie.getLastOnPath("a"));
    Trie aReduced = trie.reduce(new Lift(false));
    String aAfter = String.valueOf(trie.getLastOnPath("a"));
    boolean a = !aBefore.equals(aAfter);
    changed |= a;
    System.out.println("CHECK A reduce(Lift(false)) input getLastOnPath(\"a\") before=" + aBefore + " after=" + aAfter
        + " sameObject=" + (aReduced == trie) + " -> " + (a ? "INPUT_CHANGED" : "input_unchanged"));

    // B: Gener on a private copy of the bundled table.
    Trie copy = loadBundledCopy();
    String bBefore = stems(copy);
    Trie bReduced = copy.reduce(new Gener());
    String bAfter = stems(copy);
    boolean b = !bBefore.equals(bAfter);
    changed |= b;
    System.out.println("CHECK B reduce(Gener) private table copy stems before=" + bBefore + " after=" + bAfter
        + " sameObject=" + (bReduced == copy) + " -> " + (b ? "INPUT_CHANGED" : "input_unchanged"));

    // D (control): Optimizer on another private copy.
    Trie copy2 = loadBundledCopy();
    String dBefore = stems(copy2);
    copy2.reduce(new Optimizer());
    String dAfter = stems(copy2);
    boolean d = !dBefore.equals(dAfter);
    System.out.println("CHECK D (control) reduce(Optimizer) private table copy stems before=" + dBefore + " after="
        + dAfter + " -> " + (d ? "INPUT_CHANGED" : "input_unchanged"));

    // C: Gener on the shared default table (getDefaultTable is public only from 4.0.0 on).
    Method getDefaultTable = null;
    try {
      getDefaultTable = PolishAnalyzer.class.getMethod("getDefaultTable");
    } catch (NoSuchMethodException e) {
      // API absent in this release
    }
    String c;
    if (getDefaultTable == null) {
      c = "API_ABSENT";
      System.out.println("CHECK C PolishAnalyzer.getDefaultTable() not present in this release -> API_ABSENT");
    } else {
      String cBefore = terms(newPolishAnalyzer());
      Trie def = (Trie) getDefaultTable.invoke(null);
      Trie cReduced = def.reduce(new Gener());
      String cAfter = terms(newPolishAnalyzer());
      boolean cc = !cBefore.equals(cAfter);
      changed |= cc;
      c = cc ? "INPUT_CHANGED" : "input_unchanged";
      System.out.println("CHECK C reduce(Gener) of getDefaultTable(): terms of a new PolishAnalyzer before=" + cBefore
          + " after=" + cAfter + " sameObject=" + (cReduced == def) + " -> " + c);
    }

    System.out.println("RESULT A=" + (a ? "INPUT_CHANGED" : "input_unchanged") + " B="
        + (b ? "INPUT_CHANGED" : "input_unchanged") + " C=" + c + " D(control)="
        + (d ? "INPUT_CHANGED" : "input_unchanged"));
    System.exit(changed ? 1 : 0);
  }
}
