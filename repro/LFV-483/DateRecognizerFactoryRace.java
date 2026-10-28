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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.miscellaneous.DateRecognizerFilterFactory;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

/**
 * Version-portable main() repro (public API only; compiles against Lucene 5.5.0 .. main).
 *
 * <p>Builds an Analyzer from the dateRecognizer factory (CustomAnalyzer, the documented
 * datePattern + locale configuration) and analyses the same text from several threads. Analyzer
 * keeps one set of components per thread, so every thread must get the single-threaded tokens.
 * DateRecognizerFilterFactory hands one DateFormat to every filter it creates, so concurrent
 * analysis throws runtime exceptions out of incrementToken().
 *
 * <p>Exit status: 0 = every analysis in every thread gave the single-threaded tokens; non-zero
 * (AssertionError) = at least one analysis threw or gave different tokens.
 *
 * <p>The reference to DateRecognizerFilterFactory.class makes this file fail to compile on
 * versions that do not have the factory.
 */
public class DateRecognizerFactoryRace {

  static final String TEXT = "2024-01-15 foo 1999-12-31 bar 2001-07-04 baz 1970-01-01";
  static final List<String> EXPECTED =
      Arrays.asList("2024-01-15", "1999-12-31", "2001-07-04", "1970-01-01");
  static final int THREADS = 4;
  static final int ITERATIONS = 50_000;

  static List<String> terms(Analyzer a, String text) throws IOException {
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

  /** Analyses TEXT ITERATIONS times in each of THREADS threads; returns the number of bad analyses. */
  static int hammer(Analyzer a, Map<String, Integer> kinds, AtomicReference<String> firstBad)
      throws InterruptedException {
    final AtomicInteger bad = new AtomicInteger();
    final ConcurrentHashMap<String, AtomicInteger> byKind = new ConcurrentHashMap<>();
    final CountDownLatch startingGun = new CountDownLatch(1);
    Thread[] threads = new Thread[THREADS];
    for (int t = 0; t < THREADS; t++) {
      threads[t] =
          new Thread(
              () -> {
                try {
                  startingGun.await();
                } catch (InterruptedException e) {
                  throw new RuntimeException(e);
                }
                for (int i = 0; i < ITERATIONS; i++) {
                  String kind = null;
                  String detail = null;
                  try {
                    List<String> got = terms(a, TEXT);
                    if (got.equals(EXPECTED) == false) {
                      kind = "wrong-tokens";
                      detail = "wrong tokens: " + got;
                    }
                  } catch (Throwable e) {
                    kind = e.getClass().getName();
                    detail = e.toString();
                  }
                  if (kind != null) {
                    bad.incrementAndGet();
                    byKind.computeIfAbsent(kind, k -> new AtomicInteger()).incrementAndGet();
                    firstBad.compareAndSet(null, detail);
                  }
                }
              });
      threads[t].start();
    }
    startingGun.countDown();
    for (Thread t : threads) {
      t.join();
    }
    for (Map.Entry<String, AtomicInteger> e : byKind.entrySet()) {
      kinds.put(e.getKey(), e.getValue().get());
    }
    return bad.get();
  }

  public static void main(String[] args) throws Exception {
    System.out.println("factory class: " + DateRecognizerFilterFactory.class.getName());
    System.out.println("lucene core: " + Analyzer.class.getPackage().getImplementationVersion());
    System.out.println("java: " + System.getProperty("java.version"));
    Analyzer a =
        CustomAnalyzer.builder()
            .withTokenizer("whitespace")
            .addTokenFilter("dateRecognizer", "datePattern", "yyyy-MM-dd", "locale", "en-US")
            .build();
    List<String> single = terms(a, TEXT);
    System.out.println("single-threaded tokens: " + single);
    if (single.equals(EXPECTED) == false) {
      throw new AssertionError("single-threaded tokens differ from " + EXPECTED + ": " + single);
    }
    Map<String, Integer> kinds = new TreeMap<>();
    AtomicReference<String> firstBad = new AtomicReference<>();
    int bad = hammer(a, kinds, firstBad);
    int total = THREADS * ITERATIONS;
    System.out.println(
        "RESULT factory-built analyzer: bad=" + bad + " of " + total + " analyses; kinds=" + kinds);
    a.close();
    if (bad > 0) {
      throw new AssertionError(
          "factory-built analyzer: "
              + bad
              + " of "
              + total
              + " concurrent analyses did not give the single-threaded tokens "
              + kinds
              + "; first: "
              + firstBad.get());
    }
  }
}
