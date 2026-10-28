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
package org.apache.lucene.analysis.miscellaneous;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * DateRecognizerFilterFactory builds one java.text.DateFormat in its constructor and hands that
 * same instance to every DateRecognizerFilter it creates. DateFormat is not thread-safe, so an
 * Analyzer built from the factory (CustomAnalyzer here) fails when it is used from several threads
 * at once, although Analyzer keeps one set of components per thread. The same filter built through
 * its public constructor with one DateFormat per component does not fail.
 */
public class TestDateRecognizerFactoryThreads extends BaseTokenStreamTestCase {

  private static final String TEXT = "2024-01-15 foo 1999-12-31 bar 2001-07-04 baz 1970-01-01";
  private static final int THREADS = 4;
  private static final int ITERATIONS = 20_000;

  private static List<String> terms(Analyzer a, String text) throws IOException {
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

  /** Runs THREADS threads that analyse TEXT ITERATIONS times; returns the first failure or null. */
  private static String hammer(Analyzer a, List<String> expected, AtomicInteger failures)
      throws InterruptedException {
    AtomicReference<String> first = new AtomicReference<>();
    CountDownLatch startingGun = new CountDownLatch(1);
    Thread[] threads = new Thread[THREADS];
    for (int t = 0; t < THREADS; t++) {
      threads[t] =
          new Thread(
              () -> {
                try {
                  startingGun.await();
                  for (int i = 0; i < ITERATIONS && first.get() == null; i++) {
                    List<String> got;
                    try {
                      got = terms(a, TEXT);
                    } catch (Throwable e) {
                      failures.incrementAndGet();
                      first.compareAndSet(null, "exception at iteration " + i + ": " + e);
                      return;
                    }
                    if (got.equals(expected) == false) {
                      failures.incrementAndGet();
                      first.compareAndSet(null, "wrong tokens at iteration " + i + ": " + got);
                      return;
                    }
                  }
                } catch (InterruptedException e) {
                  throw new RuntimeException(e);
                }
              });
      threads[t].start();
    }
    startingGun.countDown();
    for (Thread t : threads) {
      t.join();
    }
    return first.get();
  }

  /** Control: the public constructor with one DateFormat per component is thread-safe. */
  public void testConstructorPerComponentDateFormatIsThreadSafe() throws Exception {
    Analyzer a =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer t = new WhitespaceTokenizer();
            return new TokenStreamComponents(
                t, new DateRecognizerFilter(t, new SimpleDateFormat("yyyy-MM-dd", Locale.US)));
          }
        };
    List<String> expected = terms(a, TEXT);
    assertEquals(List.of("2024-01-15", "1999-12-31", "2001-07-04", "1970-01-01"), expected);
    AtomicInteger failures = new AtomicInteger();
    String first = hammer(a, expected, failures);
    assertNull("constructor-built analyzer failed: " + first, first);
    a.close();
  }

  /** The factory-built analyzer must give the same tokens in every thread. */
  public void testFactoryBuiltAnalyzerIsThreadSafe() throws Exception {
    Analyzer a =
        CustomAnalyzer.builder()
            .withTokenizer("whitespace")
            .addTokenFilter("dateRecognizer", "datePattern", "yyyy-MM-dd", "locale", "en-US")
            .build();
    List<String> expected = terms(a, TEXT);
    // single-threaded, the factory-built analyzer equals the constructor-built one
    assertEquals(List.of("2024-01-15", "1999-12-31", "2001-07-04", "1970-01-01"), expected);
    AtomicInteger failures = new AtomicInteger();
    String first = hammer(a, expected, failures);
    assertNull(
        "factory-built analyzer gave a different result in a concurrent thread ("
            + failures.get()
            + " failing threads): "
            + first,
        first);
    a.close();
  }
}
