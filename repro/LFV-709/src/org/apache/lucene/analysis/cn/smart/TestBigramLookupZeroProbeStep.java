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
package org.apache.lucene.analysis.cn.smart;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The same Chinese sentence is analyzed with ASCII punctuation and with full-width punctuation. Both produce the
 * same tokens, but the ASCII version is about 100x slower: for some bigram keys "word@," the second hash of
 * BigramDictionary (the double-hashing probe step) is 0, so the lookup probes the same occupied slot until it
 * has made PRIME_BIGRAM_LENGTH (402137) attempts.
 */
public class TestBigramLookupZeroProbeStep extends LuceneTestCase {

  static final String ASCII = "我们要发展经济和文化,加强法律,增加资金,继续努力,共同进步.";
  static final String FULL_WIDTH = "我们要发展经济和文化，加强法律，增加资金，继续努力，共同进步。";

  static List<String> tokens(Analyzer a, String text) throws Exception {
    List<String> out = new ArrayList<>();
    try (TokenStream ts = a.tokenStream("f", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        out.add(term.toString());
      }
      ts.end();
    }
    return out;
  }

  static long cpuNanos(Analyzer a, String text) throws Exception {
    ThreadMXBean mx = ManagementFactory.getThreadMXBean();
    long t0 = mx.getCurrentThreadCpuTime();
    tokens(a, text);
    return mx.getCurrentThreadCpuTime() - t0;
  }

  public void testAsciiPunctuationIsNotOrdersOfMagnitudeSlower() throws Exception {
    try (Analyzer a = new SmartChineseAnalyzer()) {
      // Same segmentation either way.
      assertEquals(tokens(a, FULL_WIDTH), tokens(a, ASCII));

      StringBuilder ascii = new StringBuilder();
      StringBuilder full = new StringBuilder();
      for (int i = 0; i < 100; i++) {
        ascii.append(ASCII).append('\n');
        full.append(FULL_WIDTH).append('\n');
      }
      // warm up, then take the minimum of several runs
      for (int i = 0; i < 2; i++) {
        cpuNanos(a, ascii.toString());
        cpuNanos(a, full.toString());
      }
      long tAscii = Long.MAX_VALUE, tFull = Long.MAX_VALUE;
      for (int i = 0; i < 5; i++) {
        tAscii = Math.min(tAscii, cpuNanos(a, ascii.toString()));
        tFull = Math.min(tFull, cpuNanos(a, full.toString()));
      }
      double ratio = (double) tAscii / Math.max(1, tFull);
      String msg =
          String.format(
              java.util.Locale.ROOT,
              "%d chars: ASCII punctuation %.1f ms CPU, full-width punctuation %.1f ms CPU, ratio %.0fx",
              ascii.length(),
              tAscii / 1e6,
              tFull / 1e6,
              ratio);
      System.out.println(msg);
      assertTrue("ASCII punctuation makes analysis far slower: " + msg, ratio < 10);
    }
  }
}
