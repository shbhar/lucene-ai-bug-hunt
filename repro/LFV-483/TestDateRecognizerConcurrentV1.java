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
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * Independent check: an analyzer built from the "dateRecognizer" SPI factory (CustomAnalyzer)
 * must give the same tokens from several threads as it gives single-threaded, as an analyzer built
 * by hand with one DateFormat per component does. Uses public API only.
 */
public class TestDateRecognizerConcurrentV1 extends BaseTokenStreamTestCase {

  private static final String[] INPUTS = {
    "2024-01-15", "1999-12-31", "not-a-date", "2000-02-29", "hello", "1970-07-04"
  };
  private static final int THREADS = 4;
  private static final int ITERS = 20_000;

  private static Analyzer factoryAnalyzer(String... filterArgs) throws IOException {
    return CustomAnalyzer.builder()
        .withTokenizer("keyword")
        .addTokenFilter("dateRecognizer", filterArgs)
        .build();
  }

  private static Analyzer perComponentAnalyzer() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tk = new KeywordTokenizer();
        return new TokenStreamComponents(
            tk,
            new DateRecognizerFilter(
                tk, new SimpleDateFormat("yyyy-MM-dd", Locale.forLanguageTag("en-US"))));
      }
    };
  }

  private static List<String> analyze(Analyzer a, String text) throws IOException {
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

  /**
   * Runs the same inputs from THREADS threads; returns null if every concurrent analysis equals the
   * single-threaded result, otherwise a summary of the mismatches.
   */
  private static String hammer(Analyzer a, String[] inputs) throws Exception {
    final List<List<String>> expected = new ArrayList<>();
    for (String s : inputs) {
      expected.add(analyze(a, s));
    }
    final CountDownLatch start = new CountDownLatch(1);
    final AtomicLong total = new AtomicLong();
    final AtomicInteger bad = new AtomicInteger();
    final AtomicReference<String> first = new AtomicReference<>();
    final Map<String, Integer> kinds = new TreeMap<>();
    Thread[] threads = new Thread[THREADS];
    for (int t = 0; t < THREADS; t++) {
      threads[t] =
          new Thread(
              () -> {
                try {
                  start.await();
                } catch (InterruptedException e) {
                  throw new RuntimeException(e);
                }
                for (int i = 0; i < ITERS; i++) {
                  for (int j = 0; j < inputs.length; j++) {
                    total.incrementAndGet();
                    String problem = null;
                    String kind = null;
                    try {
                      List<String> got = analyze(a, inputs[j]);
                      if (!got.equals(expected.get(j))) {
                        kind = "wrong-tokens";
                        problem = "input=" + inputs[j] + " expected=" + expected.get(j) + " got=" + got;
                      }
                    } catch (Throwable e) {
                      kind = e.getClass().getSimpleName();
                      problem = "input=" + inputs[j] + " threw " + e;
                    }
                    if (problem != null) {
                      bad.incrementAndGet();
                      first.compareAndSet(null, problem);
                      synchronized (kinds) {
                        kinds.merge(kind, 1, Integer::sum);
                      }
                    }
                  }
                }
              });
      threads[t].start();
    }
    start.countDown();
    for (Thread th : threads) {
      th.join();
    }
    String summary =
        bad.get() == 0
            ? null
            : bad.get()
                + " of "
                + total.get()
                + " concurrent analyses differ from the single-threaded result; kinds="
                + kinds
                + "; first: "
                + first.get();
    System.out.println("hammer: " + (summary == null ? "0 of " + total.get() + " bad" : summary));
    return summary;
  }

  /** Single-threaded: the factory-built and the hand-built analyzer agree. */
  public void testSingleThreadedSameTokens() throws Exception {
    Analyzer fa = factoryAnalyzer("datePattern", "yyyy-MM-dd", "locale", "en-US");
    Analyzer pa = perComponentAnalyzer();
    for (String s : INPUTS) {
      assertEquals(s, analyze(pa, s), analyze(fa, s));
    }
    assertAnalyzesTo(fa, "2024-01-15", new String[] {"2024-01-15"});
    assertAnalyzesTo(fa, "hello", new String[0]);
    fa.close();
    pa.close();
  }

  /** Control: one DateFormat per component is thread-safe. */
  public void testPerComponentDateFormatConcurrent() throws Exception {
    Analyzer pa = perComponentAnalyzer();
    assertNull(hammer(pa, INPUTS));
    pa.close();
  }

  /** The claim: the factory-built analyzer is not thread-safe. */
  public void testFactoryAnalyzerConcurrent() throws Exception {
    Analyzer fa = factoryAnalyzer("datePattern", "yyyy-MM-dd", "locale", "en-US");
    assertNull(hammer(fa, INPUTS));
    fa.close();
  }

  /** Same claim with the factory's no-argument configuration (default DateFormat). */
  public void testFactoryAnalyzerDefaultFormatConcurrent() throws Exception {
    Analyzer fa = factoryAnalyzer();
    String[] inputs = {"Jan 15, 2024", "Dec 31, 1999", "hello", "Jul 4, 1970"};
    assertEquals(List.of("Jan 15, 2024"), analyze(fa, "Jan 15, 2024"));
    assertNull(hammer(fa, inputs));
    fa.close();
  }

  /** User-facing path: IndexWriter (documented thread-safe) shared by several indexing threads. */
  public void testConcurrentAddDocument() throws Exception {
    Analyzer fa = factoryAnalyzer("datePattern", "yyyy-MM-dd", "locale", "en-US");
    final int docsPerThread = 10_000;
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(fa))) {
      final CountDownLatch start = new CountDownLatch(1);
      final AtomicInteger failed = new AtomicInteger();
      final AtomicReference<Throwable> first = new AtomicReference<>();
      Thread[] threads = new Thread[THREADS];
      for (int t = 0; t < THREADS; t++) {
        threads[t] =
            new Thread(
                () -> {
                  try {
                    start.await();
                  } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                  }
                  for (int i = 0; i < docsPerThread; i++) {
                    Document doc = new Document();
                    doc.add(new TextField("date", INPUTS[i % INPUTS.length], Field.Store.NO));
                    try {
                      w.addDocument(doc);
                    } catch (Throwable e) {
                      failed.incrementAndGet();
                      first.compareAndSet(null, e);
                    }
                  }
                });
        threads[t].start();
      }
      start.countDown();
      for (Thread th : threads) {
        th.join();
      }
      w.commit();
      int numDocs;
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        numDocs = r.numDocs();
      }
      System.out.println(
          "addDocument: failed=" + failed.get() + " numDocs=" + numDocs + " first=" + first.get());
      assertEquals(
          "addDocument calls that threw (first: " + first.get() + ")", 0, failed.get());
      assertEquals(THREADS * docsPerThread, numDocs);
    }
    fa.close();
  }
}
