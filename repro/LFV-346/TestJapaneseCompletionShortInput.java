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
import java.util.HashSet;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;

/** A 32-character kana string must not exhaust the heap in JapaneseCompletionAnalyzer. */
public class TestJapaneseCompletionShortInput extends LuceneTestCase {

  /** Analyzes the text; returns {tokens, distinct terms}. */
  private static long[] analyze(Analyzer a, String text) throws IOException {
    long tokens = 0;
    Set<String> distinct = new HashSet<>();
    try (TokenStream ts = a.tokenStream("f", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        tokens++;
        distinct.add(term.toString());
      }
      ts.end();
    }
    return new long[] {tokens, distinct.size()};
  }

  /** INDEX mode (the default): "ン" is typed "n" or "nn". */
  public void testIndexModeKatakanaRun() throws IOException {
    try (Analyzer a = new JapaneseCompletionAnalyzer()) {
      StringBuilder growth = new StringBuilder();
      for (int n : new int[] {8, 12, 16}) {
        long[] r = analyze(a, "ン".repeat(n));
        growth.append(" n=").append(n).append(": ").append(r[0]).append(" tokens, ");
        growth.append(r[1]).append(" distinct;");
      }
      try {
        analyze(a, "ン".repeat(32));
      } catch (OutOfMemoryError e) {
        throw new AssertionError("\"ン\" x 32 exhausted the heap; \"ン\" x n gave" + growth, e);
      }
    }
  }

  /** QUERY mode: consecutive kana are joined into one reading; "し" is typed "si" or "shi". */
  public void testQueryModeHiraganaRun() throws IOException {
    try (Analyzer a = new JapaneseCompletionAnalyzer(null, JapaneseCompletionFilter.Mode.QUERY)) {
      try {
        analyze(a, "し".repeat(32));
      } catch (OutOfMemoryError e) {
        throw new AssertionError("\"し\" x 32 exhausted the heap", e);
      }
    }
  }
}
