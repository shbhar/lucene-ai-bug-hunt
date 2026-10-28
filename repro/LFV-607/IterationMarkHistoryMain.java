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
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.ja.JapaneseIterationMarkCharFilter;

/**
 * Version-independent repro (plain main(), no test framework) for every release that has
 * JapaneseIterationMarkCharFilter (4.0.0-BETA onwards). Uses only the public Reader constructor
 * with default flags.
 *
 * <p>Exit codes: 0 = fixed (all checks pass), 1 = bug reproduces (some unvoiced-mark expansion
 * after っ, な..の, ぱ ぴ ぷ ぺ or their katakana forms is a different kana), 3 = a control that the
 * upstream tests assert failed (the filter behaves differently in some other way; investigate).
 */
public class IterationMarkHistoryMain {

  static String filter(String s) throws Exception {
    Reader r = new JapaneseIterationMarkCharFilter(new StringReader(s));
    try {
      StringBuilder sb = new StringBuilder();
      int c;
      while ((c = r.read()) != -1) {
        sb.append((char) c);
      }
      return sb.toString();
    } finally {
      r.close();
    }
  }

  public static void main(String[] args) throws Exception {
    // Controls: behaviour that the upstream TestJapaneseIterationMarkCharFilter asserts.
    String[][] controls = {
      {"じゝ", "じし"}, // じゝ -> じし
      {"ぽゝ", "ぽぽ"}, // ぽゝ -> ぽぽ
      {"こゝ", "ここ"}, // こゝ -> ここ
      {"コヽ", "ココ"}, // コヽ -> ココ
    };
    int controlFailures = 0;
    for (String[] c : controls) {
      String got = filter(c[0]);
      boolean ok = got.equals(c[1]);
      System.out.println("control " + c[0] + " -> " + got + (ok ? " ok" : " UNEXPECTED (expected " + c[1] + ")"));
      if (!ok) controlFailures++;
    }

    // The word from the report: なゝめ (ななめ, "diagonal").
    String word = filter("なゝめ");
    boolean wordOk = word.equals("ななめ");
    System.out.println("word なゝめ -> " + word + (wordOk ? " ok" : " WRONG (expected ななめ)"));

    // All 20 affected pairs: kana with no voiced form, followed by the unvoiced iteration mark.
    String hiragana = "っなにぬねのぱぴぷぺ"; // っなにぬねのぱぴぷぺ
    String katakana = "ッナニヌネノパピプペ"; // ッナニヌネノパピプペ
    List<String> wrong = new ArrayList<String>();
    for (int i = 0; i < hiragana.length(); i++) {
      check(hiragana.charAt(i), 'ゝ', wrong);
      check(katakana.charAt(i), 'ヽ', wrong);
    }
    System.out.println("affected pairs wrong: " + wrong.size() + "/20 " + wrong);

    if (controlFailures > 0) {
      System.out.println("RESULT: CONTROL_FAILURE (" + controlFailures + ")");
      System.exit(3);
    }
    if (!wordOk || !wrong.isEmpty()) {
      System.out.println("RESULT: BUG_REPRODUCES");
      System.exit(1);
    }
    System.out.println("RESULT: FIXED");
  }

  static void check(char kana, char mark, List<String> wrong) throws Exception {
    String in = "" + kana + mark;
    String got = filter(in);
    String expected = "" + kana + kana;
    if (!got.equals(expected)) {
      wrong.add(in + "->" + got);
    }
  }
}
