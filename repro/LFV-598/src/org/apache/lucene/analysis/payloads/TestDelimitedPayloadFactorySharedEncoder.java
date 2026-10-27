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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizerFactory;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.tokenattributes.PayloadAttribute;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * DelimitedPayloadTokenFilterFactory instantiates a custom encoder class ONCE (in inform()) and hands that one
 * instance to every DelimitedPayloadTokenFilter it creates, i.e. to the per-thread components of every thread using
 * the analyzer. Nothing documents that a custom PayloadEncoder must be thread-safe, and the sibling factories that load
 * a class by name (PhoneticFilterFactory, SnowballPorterFilterFactory) create a new instance for every filter.
 *
 * <p>The encoder below is correct for any single TokenStream: it reuses one BytesRef (as attribute producers commonly
 * do), and PayloadAttributeImpl deep-copies the payload on clone() and copyTo(). Through the factory, a second thread's
 * token overwrites the first thread's payload between incrementToken() and the consumer reading it.
 */
public class TestDelimitedPayloadFactorySharedEncoder extends BaseTokenStreamTestCase {

  /** Allocation-free float encoder: returns the same BytesRef every call. Public with a no-arg constructor. */
  public static final class ReusingFloatEncoder extends AbstractEncoder {
    private final BytesRef scratch = new BytesRef(new byte[4]);

    public ReusingFloatEncoder() {}

    @Override
    public BytesRef encode(char[] buffer, int offset, int length) {
      int bits = Float.floatToIntBits(Float.parseFloat(new String(buffer, offset, length)));
      scratch.bytes[0] = (byte) (bits >>> 24);
      scratch.bytes[1] = (byte) (bits >>> 16);
      scratch.bytes[2] = (byte) (bits >>> 8);
      scratch.bytes[3] = (byte) bits;
      scratch.offset = 0;
      scratch.length = 4;
      return scratch;
    }
  }

  private static float decode(BytesRef p) {
    return PayloadHelper.decodeFloat(p.bytes, p.offset);
  }

  /**
   * Thread A reads token "x|1.0", then (before the consumer looks at the payload, as IndexWriter does right after
   * incrementToken()) thread B analyses "y|2.0" with the same Analyzer. Returns the payload thread A then sees.
   */
  private static float interleaved(Analyzer a) throws Exception {
    CountDownLatch aHasToken = new CountDownLatch(1);
    CountDownLatch bDone = new CountDownLatch(1);
    AtomicReference<Throwable> failure = new AtomicReference<>();
    AtomicReference<Float> aPayload = new AtomicReference<>();
    Thread ta =
        new Thread(
            () -> {
              try (TokenStream ts = a.tokenStream("f", "x|1.0")) {
                PayloadAttribute pay = ts.addAttribute(PayloadAttribute.class);
                ts.reset();
                assertTrue(ts.incrementToken());
                aHasToken.countDown();
                assertTrue(bDone.await(30, TimeUnit.SECONDS));
                aPayload.set(decode(pay.getPayload()));
                ts.end();
              } catch (Throwable t) {
                failure.set(t);
              }
            });
    Thread tb =
        new Thread(
            () -> {
              try {
                assertTrue(aHasToken.await(30, TimeUnit.SECONDS));
                try (TokenStream ts = a.tokenStream("f", "y|2.0")) {
                  PayloadAttribute pay = ts.addAttribute(PayloadAttribute.class);
                  ts.reset();
                  assertTrue(ts.incrementToken());
                  assertEquals(2.0f, decode(pay.getPayload()), 0f);
                  ts.end();
                }
              } catch (Throwable t) {
                failure.set(t);
              } finally {
                bDone.countDown();
              }
            });
    ta.start();
    tb.start();
    ta.join();
    tb.join();
    if (failure.get() != null) throw new AssertionError(failure.get());
    return aPayload.get();
  }

  /** Control: the same filter through its public constructor, one encoder per filter. Passes. */
  public void testConstructorWithEncoderPerFilter() throws Exception {
    Analyzer a =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer t = new WhitespaceTokenizer();
            return new TokenStreamComponents(
                t, new DelimitedPayloadTokenFilter(t, '|', new ReusingFloatEncoder()));
          }
        };
    assertEquals(1.0f, interleaved(a), 0f);
    a.close();
  }

  /** The factory-built analyzer: thread A's token carries thread B's payload. Fails on main. */
  public void testFactoryBuiltAnalyzerKeepsEachThreadsPayload() throws Exception {
    Analyzer a =
        CustomAnalyzer.builder()
            .withTokenizer(WhitespaceTokenizerFactory.class)
            .addTokenFilter(
                DelimitedPayloadTokenFilterFactory.class,
                "encoder",
                ReusingFloatEncoder.class.getName(),
                "delimiter",
                "|")
            .build();
    // single-threaded the factory-built analyzer is correct
    try (TokenStream ts = a.tokenStream("f", "x|1.0")) {
      PayloadAttribute pay = ts.addAttribute(PayloadAttribute.class);
      ts.reset();
      assertTrue(ts.incrementToken());
      assertEquals(1.0f, decode(pay.getPayload()), 0f);
      ts.end();
    }
    assertEquals(
        "thread A's token x|1.0 must keep payload 1.0 while another thread uses the same analyzer",
        1.0f,
        interleaved(a),
        0f);
    a.close();
  }

  /**
   * Impact on an index (not deterministic: no latch, ordinary concurrent IndexWriter use): THREADS threads add
   * documents "t<i>|<i>" through one IndexWriter whose analyzer is factory-built; every document's term must carry its
   * own payload. Reports how many documents got another document's payload.
   */
  public void testConcurrentIndexingStoresEachDocumentsPayload() throws Exception {
    Analyzer a =
        CustomAnalyzer.builder()
            .withTokenizer(WhitespaceTokenizerFactory.class)
            .addTokenFilter(
                DelimitedPayloadTokenFilterFactory.class,
                "encoder",
                ReusingFloatEncoder.class.getName(),
                "delimiter",
                "|")
            .build();
    assertEquals("factory-built analyzer", 0, wrongPayloadsAfterConcurrentIndexing(a));
  }

  /** Control for the index-level test: one encoder per filter (public constructor). Passes. */
  public void testConcurrentIndexingWithEncoderPerFilter() throws Exception {
    Analyzer a =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer t = new WhitespaceTokenizer();
            return new TokenStreamComponents(
                t, new DelimitedPayloadTokenFilter(t, '|', new ReusingFloatEncoder()));
          }
        };
    assertEquals("constructor-built analyzer", 0, wrongPayloadsAfterConcurrentIndexing(a));
  }

  private static int wrongPayloadsAfterConcurrentIndexing(Analyzer a) throws Exception {
    final int threads = 4, perThread = 5000;
    try (org.apache.lucene.store.Directory dir = new org.apache.lucene.store.ByteBuffersDirectory()) {
      try (org.apache.lucene.index.IndexWriter w =
          new org.apache.lucene.index.IndexWriter(dir, new org.apache.lucene.index.IndexWriterConfig(a))) {
        Thread[] ts = new Thread[threads];
        AtomicReference<Throwable> failure = new AtomicReference<>();
        java.util.concurrent.CyclicBarrier start = new java.util.concurrent.CyclicBarrier(threads);
        for (int t = 0; t < threads; t++) {
          final int base = t * perThread;
          ts[t] =
              new Thread(
                  () -> {
                    try {
                      start.await();
                      for (int i = base; i < base + perThread; i++) {
                        org.apache.lucene.document.Document d = new org.apache.lucene.document.Document();
                        d.add(new org.apache.lucene.document.TextField("f", "t" + i + "|" + i, org.apache.lucene.document.Field.Store.NO));
                        w.addDocument(d);
                      }
                    } catch (Throwable e) {
                      failure.set(e);
                    }
                  });
          ts[t].start();
        }
        for (Thread t : ts) t.join();
        if (failure.get() != null) throw new AssertionError(failure.get());
      }
      int wrong = 0;
      String example = null;
      try (org.apache.lucene.index.DirectoryReader r = org.apache.lucene.index.DirectoryReader.open(dir)) {
        for (int i = 0; i < threads * perThread; i++) {
          org.apache.lucene.index.PostingsEnum pe =
              org.apache.lucene.index.MultiTerms.getTermPostingsEnum(
                  r, "f", new BytesRef("t" + i), org.apache.lucene.index.PostingsEnum.PAYLOADS);
          assertNotNull(pe);
          assertTrue(pe.nextDoc() != org.apache.lucene.index.PostingsEnum.NO_MORE_DOCS);
          pe.nextPosition();
          float got = decode(pe.getPayload());
          if (got != (float) i) {
            wrong++;
            if (example == null) example = "term t" + i + " has payload " + got;
          }
        }
      }
      if (wrong > 0)
        System.out.println(
            "documents (of " + threads * perThread + ") whose indexed payload belongs to another document: "
                + wrong + ", e.g. " + example);
      a.close();
      return wrong;
    }
  }
}
