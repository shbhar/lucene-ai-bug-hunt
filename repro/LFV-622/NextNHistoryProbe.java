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
package org.apache.lucene.analysis.opennlp;

import java.io.InputStream;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import opennlp.tools.sentdetect.SentenceModel;
import org.apache.lucene.analysis.opennlp.tools.NLPSentenceDetectorOp;

/**
 * History probe for OpenNLPSentenceBreakIterator.next(int) at the last boundary. Uses only API that
 * exists unchanged from 7.3.0 to main (no test-framework), so the same source runs on every release.
 *
 * <p>P1 (no model): one-sentence text; next() from first() vs next(1) from first().
 *
 * <p>P2 (the release's own en-test-sent.bin on the classpath): the three-sentence text of
 * TestOpenNLPSentenceBreakIterator; for n = 1..3, next(n) from first() vs n calls of next().
 *
 * <p>Controls (must hold on every release, the upstream test asserts them): next(4) == DONE,
 * next(2) == 59, then next(-2) == 0.
 *
 * <p>Prints RESULT=affected | not_affected | control_failed. Exits non-zero (AssertionError) when
 * the bug reproduces or a control fails.
 */
public class NextNHistoryProbe {
  static final String ONE = "Whole text is one sentence.";
  static final String TEXT =
      "Sentence number 1 has 6 words. Sentence number 2, 5 words. And finally, sentence number 3 has 8 words.";

  public static void main(String[] args) throws Exception {
    List<String> bugs = new ArrayList<>();
    List<String> controlFails = new ArrayList<>();

    // P1: no model, the whole text is one sentence: boundaries 0 and ONE.length()
    BreakIterator bi = new OpenNLPSentenceBreakIterator(new NLPSentenceDetectorOp());
    bi.setText(ONE);
    bi.first();
    int viaNext = bi.next();
    bi.first();
    int viaNext1 = bi.next(1);
    int cur1 = bi.current();
    System.out.println(
        "P1 no-model len=" + ONE.length() + " next()=" + viaNext + " next(1)=" + viaNext1
            + " current()-after-next(1)=" + cur1);
    if (viaNext != ONE.length()) {
      controlFails.add("P1 next() from first() = " + viaNext + ", expected " + ONE.length());
    }
    if (viaNext1 != viaNext) {
      bugs.add("P1 next(1)=" + viaNext1 + " but next()=" + viaNext);
    }

    // P2: the release's own test sentence model
    InputStream in = NextNHistoryProbe.class.getResourceAsStream("en-test-sent.bin");
    if (in == null) {
      controlFails.add("P2 en-test-sent.bin not on the classpath");
    } else {
      SentenceModel model;
      try (InputStream is = in) {
        model = new SentenceModel(is);
      }
      bi = new OpenNLPSentenceBreakIterator(new NLPSentenceDetectorOp(model));
      bi.setText(TEXT);
      List<Integer> boundaries = new ArrayList<>();
      boundaries.add(bi.first());
      for (int b = bi.next(); b != BreakIterator.DONE; b = bi.next()) {
        boundaries.add(b);
      }
      System.out.println("P2 model boundaries via first()/next(): " + boundaries);
      if (!boundaries.toString().equals("[0, 31, 59, 102]")) {
        controlFails.add("P2 boundaries " + boundaries + ", expected [0, 31, 59, 102]");
      }
      StringBuilder line = new StringBuilder("P2 next(n) from first():");
      for (int n = 1; n < boundaries.size(); n++) {
        bi.first();
        int got = bi.next(n);
        int expected = boundaries.get(n);
        line.append(" next(").append(n).append(")=").append(got);
        if (got != expected) {
          bugs.add("P2 next(" + n + ")=" + got + " expected " + expected);
        }
      }
      System.out.println(line);

      // controls asserted by the upstream test since 7.3.0
      bi.first();
      int past = bi.next(4);
      bi.first();
      int two = bi.next(2);
      int back = bi.next(-2);
      System.out.println("P2 controls: next(4)=" + past + " next(2)=" + two + " next(-2)=" + back);
      if (past != BreakIterator.DONE) controlFails.add("control next(4)=" + past + " expected DONE");
      if (two != 59) controlFails.add("control next(2)=" + two + " expected 59");
      if (back != 0) controlFails.add("control next(-2)=" + back + " expected 0");
    }

    if (!controlFails.isEmpty()) {
      System.out.println("RESULT=control_failed " + controlFails + " bugs=" + bugs);
      throw new AssertionError("control failed: " + controlFails);
    } else if (!bugs.isEmpty()) {
      System.out.println("RESULT=affected " + bugs);
      throw new AssertionError("next(n) returns DONE at the last boundary: " + bugs);
    } else {
      System.out.println("RESULT=not_affected");
    }
  }
}
