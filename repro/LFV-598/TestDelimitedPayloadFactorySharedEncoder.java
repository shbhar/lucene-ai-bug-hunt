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
package org.apache.lucene.analysis.payloads;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.tokenattributes.PayloadAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.MultiTerms;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.BytesRefBuilder;

/**
 * DelimitedPayloadTokenFilterFactory creates a custom PayloadEncoder once, in inform(), and passes
 * that one instance to every DelimitedPayloadTokenFilter it creates. An Analyzer builds one set of
 * components per thread, so every thread that uses the analyzer calls the same encoder. The two
 * encoders below reuse one buffer for the BytesRef they return. That is correct inside any single
 * TokenStream, but through the factory another thread overwrites a payload before the consumer has
 * read it.
 */
public class TestDelimitedPayloadFactorySharedEncoder extends BaseTokenStreamTestCase {

  private static final int THREADS = 4;

  /** Float encoder that returns the same 4-byte BytesRef on every call. */
  public static final class ReusingFloatEncoder extends AbstractEncoder {
    private final BytesRef scratch = new BytesRef(new byte[4]);

    @Override
    public BytesRef encode(char[] buffer, int offset, int length) {
      PayloadHelper.encodeFloat(
          Float.parseFloat(new String(buffer, offset, length)), scratch.bytes, 0);
      scratch.length = 4;
      return scratch;
    }
  }

  /** UTF-8 encoder that returns the BytesRef of one reused BytesRefBuilder (variable length). */
  public static final class ReusingBytesEncoder extends AbstractEncoder {
    private final BytesRefBuilder scratch = new BytesRefBuilder();

    @Override
    public BytesRef encode(char[] buffer, int offset, int length) {
      scratch.copyChars(buffer, offset, length);
      return scratch.get();
    }
  }

  private static Analyzer factoryAnalyzer(String encoder) throws IOException {
    return CustomAnalyzer.builder()
        .withTokenizer("whitespace")
        .addTokenFilter("delimitedPayload", "encoder", encoder, "delimiter", "|")
        .build();
  }

  /** The same chain through the public constructor: one encoder per filter, so one per thread. */
  private static Analyzer encoderPerFilterAnalyzer(Supplier<PayloadEncoder> encoders) {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer t = new WhitespaceTokenizer();
        return new TokenStreamComponents(
            t, new DelimitedPayloadTokenFilter(t, '|', encoders.get()));
      }
    };
  }

  /** Thread A analyses "x|1.0" and must see payload 1.0 while thread B analyses "y|2.0". */
  public void testFactoryEncoderIsSharedAcrossThreads() throws Exception {
    try (Analyzer a = factoryAnalyzer(ReusingFloatEncoder.class.getName())) {
      assertEquals(
          "thread A's token x|1.0 must keep payload 1.0 while thread B uses the same analyzer",
          1.0f,
          payloadSeenByThreadA(a),
          0f);
    }
  }

  /** THREADS threads add documents {@code "t0|0", "t1|1", ...} through one IndexWriter. */
  public void testConcurrentIndexingKeepsEachDocumentsPayload() throws Exception {
    try (Analyzer a = factoryAnalyzer(ReusingFloatEncoder.class.getName())) {
      List<String> wrong = wrongFloatPayloadsAfterConcurrentIndexing(a);
      assertEquals(
          wrong.size()
              + " of 20000 documents have another document's payload, e.g. "
              + (wrong.isEmpty() ? "-" : wrong.get(0)),
          0,
          wrong.size());
    }
  }

  /** The same with variable-length payloads: the commit must succeed. */
  public void testConcurrentIndexingWithVariableLengthPayloads() throws Exception {
    try (Analyzer a = factoryAnalyzer(ReusingBytesEncoder.class.getName())) {
      indexVariableLengthPayloadsConcurrently(a);
    }
  }

  /** Control: the same encoders, one instance per filter. Passes. */
  public void testControlEncoderPerFilter() throws Exception {
    try (Analyzer a = encoderPerFilterAnalyzer(ReusingFloatEncoder::new)) {
      assertEquals(1.0f, payloadSeenByThreadA(a), 0f);
      assertEquals(List.of(), wrongFloatPayloadsAfterConcurrentIndexing(a));
    }
    try (Analyzer a = encoderPerFilterAnalyzer(ReusingBytesEncoder::new)) {
      indexVariableLengthPayloadsConcurrently(a);
    }
  }

  /** Control: the built-in float encoder (a new BytesRef per call) through the factory. Passes. */
  public void testControlBuiltInEncoderThroughFactory() throws Exception {
    try (Analyzer a = factoryAnalyzer("float")) {
      assertEquals(1.0f, payloadSeenByThreadA(a), 0f);
      assertEquals(List.of(), wrongFloatPayloadsAfterConcurrentIndexing(a));
    }
  }

  /**
   * Thread A reads token "x|1.0" and stops before it looks at the payload (IndexWriter reads it
   * right after incrementToken() returns); thread B analyses "y|2.0" with the same Analyzer.
   * Returns the payload thread A then sees.
   */
  private static float payloadSeenByThreadA(Analyzer a) throws Exception {
    CountDownLatch aHasToken = new CountDownLatch(1);
    CountDownLatch bDone = new CountDownLatch(1);
    AtomicReference<Throwable> failure = new AtomicReference<>();
    float[] seen = new float[1];
    Thread ta =
        new Thread(
            () -> {
              try (TokenStream ts = a.tokenStream("f", "x|1.0")) {
                PayloadAttribute payload = ts.addAttribute(PayloadAttribute.class);
                ts.reset();
                assertTrue(ts.incrementToken());
                aHasToken.countDown();
                assertTrue(bDone.await(30, TimeUnit.SECONDS));
                seen[0] = decodeFloat(payload.getPayload());
                ts.end();
              } catch (Throwable t) {
                failure.compareAndSet(null, t);
              }
            });
    Thread tb =
        new Thread(
            () -> {
              try {
                assertTrue(aHasToken.await(30, TimeUnit.SECONDS));
                try (TokenStream ts = a.tokenStream("f", "y|2.0")) {
                  ts.reset();
                  assertTrue(ts.incrementToken());
                  ts.end();
                }
              } catch (Throwable t) {
                failure.compareAndSet(null, t);
              } finally {
                bDone.countDown();
              }
            });
    ta.start();
    tb.start();
    ta.join();
    tb.join();
    if (failure.get() != null) {
      throw new AssertionError(failure.get());
    }
    return seen[0];
  }

  /** Indexes {@code "t" + i + "|" + i} for i in [0, 20000) from THREADS threads. */
  private static List<String> wrongFloatPayloadsAfterConcurrentIndexing(Analyzer a)
      throws Exception {
    String[] texts = new String[20_000];
    for (int i = 0; i < texts.length; i++) {
      texts[i] = "t" + i + "|" + i;
    }
    List<String> wrong = new ArrayList<>();
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
        addConcurrently(w, texts);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        for (int i = 0; i < texts.length; i++) {
          PostingsEnum pe = firstPosition(r, "t" + i);
          float got = decodeFloat(pe.getPayload());
          if (got != i) {
            wrong.add("term t" + i + " has payload " + got);
          }
        }
      }
    }
    return wrong;
  }

  /**
   * Indexes 12000 documents {@code "c|v1 c|v2 u" + i + "|v3"} (v: 1-64 random letters) from THREADS
   * threads, commits, and checks that every unique term kept its own payload.
   */
  private static void indexVariableLengthPayloadsConcurrently(Analyzer a) throws Exception {
    Random rnd = new Random(random().nextLong());
    String[] texts = new String[12_000];
    String[] unique = new String[texts.length];
    for (int i = 0; i < texts.length; i++) {
      unique[i] = randomLetters(rnd);
      texts[i] =
          "c|" + randomLetters(rnd) + " c|" + randomLetters(rnd) + " u" + i + "|" + unique[i];
    }
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a));
      addConcurrently(w, texts);
      try {
        w.commit();
      } catch (Throwable e) {
        boolean open = w.isOpen();
        boolean tragic = w.getTragicException() != null;
        w.rollback();
        throw new AssertionError(
            "commit failed; IndexWriter still open: " + open + ", tragic exception: " + tragic, e);
      }
      w.close();
      int wrong = 0;
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        for (int i = 0; i < texts.length; i++) {
          BytesRef payload = firstPosition(r, "u" + i).getPayload();
          if (payload == null || payload.utf8ToString().equals(unique[i]) == false) {
            wrong++;
          }
        }
      }
      assertEquals(wrong + " of 12000 documents have a wrong payload", 0, wrong);
    }
  }

  private static void addConcurrently(IndexWriter w, String[] texts) throws Exception {
    CyclicBarrier start = new CyclicBarrier(THREADS);
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Thread[] workers = new Thread[THREADS];
    int perThread = texts.length / THREADS;
    for (int t = 0; t < THREADS; t++) {
      final int from = t * perThread;
      workers[t] =
          new Thread(
              () -> {
                try {
                  start.await();
                  for (int i = from; i < from + perThread; i++) {
                    Document doc = new Document();
                    doc.add(new TextField("f", texts[i], Field.Store.NO));
                    w.addDocument(doc);
                  }
                } catch (Throwable e) {
                  failure.compareAndSet(null, e);
                }
              });
      workers[t].start();
    }
    for (Thread worker : workers) {
      worker.join();
    }
    if (failure.get() != null) {
      throw new AssertionError("addDocument failed", failure.get());
    }
  }

  private static PostingsEnum firstPosition(DirectoryReader r, String term) throws IOException {
    PostingsEnum pe =
        MultiTerms.getTermPostingsEnum(r, "f", new BytesRef(term), PostingsEnum.PAYLOADS);
    assertNotNull(term, pe);
    assertTrue(pe.nextDoc() != PostingsEnum.NO_MORE_DOCS);
    pe.nextPosition();
    return pe;
  }

  private static float decodeFloat(BytesRef payload) {
    return PayloadHelper.decodeFloat(payload.bytes, payload.offset);
  }

  private static String randomLetters(Random rnd) {
    char[] chars = new char[1 + rnd.nextInt(64)];
    for (int k = 0; k < chars.length; k++) {
      chars[k] = (char) ('a' + rnd.nextInt(26));
    }
    return new String(chars);
  }
}
