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
package v1check;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.payloads.AbstractEncoder;
import org.apache.lucene.analysis.payloads.DelimitedPayloadTokenFilter;
import org.apache.lucene.analysis.payloads.DelimitedPayloadTokenFilterFactory;
import org.apache.lucene.analysis.payloads.PayloadHelper;
import org.apache.lucene.analysis.tokenattributes.PayloadAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.ClasspathResourceLoader;

/**
 * Independent check: DelimitedPayloadTokenFilterFactory with encoder=&lt;custom class&gt; hands one
 * encoder instance to every filter it creates, so a custom encoder that reuses a BytesRef (fine for
 * one TokenStream) mixes up payloads between threads that share the Analyzer.
 *
 * <p>Public API only (different package from the code under test).
 */
public class TestSharedCustomPayloadEncoder extends LuceneTestCase {

  /** A float encoder that reuses one BytesRef: correct for any single TokenStream. */
  public static final class ReusingFloatEncoder extends AbstractEncoder {
    private final BytesRef scratch = new BytesRef(new byte[4]);

    public ReusingFloatEncoder() {}

    @Override
    public BytesRef encode(char[] buffer, int offset, int length) {
      float v = Float.parseFloat(new String(buffer, offset, length));
      PayloadHelper.encodeFloat(v, scratch.bytes, 0);
      scratch.offset = 0;
      scratch.length = 4;
      return scratch;
    }
  }

  private static final String ENCODER = ReusingFloatEncoder.class.getName();

  private static Analyzer factoryAnalyzer(String encoder) throws IOException {
    return CustomAnalyzer.builder(new ClasspathResourceLoader(TestSharedCustomPayloadEncoder.class))
        .withTokenizer("whitespace")
        .addTokenFilter("delimitedPayload", "encoder", encoder, "delimiter", "|")
        .build();
  }

  /** Same chain, but one encoder instance per filter (the public constructor). */
  private static Analyzer perFilterEncoderAnalyzer() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tok = new WhitespaceTokenizer();
        return new TokenStreamComponents(
            tok, new DelimitedPayloadTokenFilter(tok, '|', new ReusingFloatEncoder()));
      }
    };
  }

  private static float payloadOf(TokenStream ts) {
    BytesRef p = ts.getAttribute(PayloadAttribute.class).getPayload();
    assertNotNull(p);
    return PayloadHelper.decodeFloat(p.bytes, p.offset);
  }

  /**
   * Factory-level, single thread: two filters created by the same factory (what two threads'
   * components get) interfere with each other.
   */
  public void testTwoFiltersFromOneFactory() throws Exception {
    Map<String, String> args = new HashMap<>();
    args.put("encoder", ENCODER);
    DelimitedPayloadTokenFilterFactory factory = new DelimitedPayloadTokenFilterFactory(args);
    factory.inform(new ClasspathResourceLoader(TestSharedCustomPayloadEncoder.class));

    Tokenizer ta = new WhitespaceTokenizer();
    ta.setReader(new StringReader("x|1.0"));
    TokenStream a = factory.create(ta);
    Tokenizer tb = new WhitespaceTokenizer();
    tb.setReader(new StringReader("y|2.0"));
    TokenStream b = factory.create(tb);

    a.reset();
    b.reset();
    assertTrue(a.incrementToken());
    assertTrue(b.incrementToken());
    assertEquals("stream b", 2.0f, payloadOf(b), 0f);
    assertEquals("stream a must keep its own payload", 1.0f, payloadOf(a), 0f);
    a.end();
    b.end();
    a.close();
    b.close();
  }

  /**
   * Same analyzer used by two threads (each thread gets its own components): thread A reads its
   * payload after thread B produced a token. Deterministic via latches.
   */
  public void testAnalyzerTwoThreads() throws Exception {
    try (Analyzer analyzer = factoryAnalyzer(ENCODER)) {
      CountDownLatch aHasToken = new CountDownLatch(1);
      CountDownLatch bDone = new CountDownLatch(1);
      AtomicReference<Float> aPayload = new AtomicReference<>();
      AtomicReference<Throwable> err = new AtomicReference<>();
      AtomicReference<TokenStream> aStream = new AtomicReference<>();
      AtomicReference<TokenStream> bStream = new AtomicReference<>();

      Thread ta =
          new Thread(
              () -> {
                try (TokenStream ts = analyzer.tokenStream("f", "x|1.0")) {
                  aStream.set(ts);
                  ts.reset();
                  assertTrue(ts.incrementToken());
                  aHasToken.countDown();
                  assertTrue(bDone.await(30, TimeUnit.SECONDS));
                  aPayload.set(payloadOf(ts));
                  while (ts.incrementToken()) {}
                  ts.end();
                } catch (Throwable t) {
                  err.compareAndSet(null, t);
                  aHasToken.countDown();
                }
              });
      Thread tb =
          new Thread(
              () -> {
                try {
                  assertTrue(aHasToken.await(30, TimeUnit.SECONDS));
                  try (TokenStream ts = analyzer.tokenStream("f", "y|2.0")) {
                    bStream.set(ts);
                    ts.reset();
                    assertTrue(ts.incrementToken());
                    assertEquals(2.0f, payloadOf(ts), 0f);
                    while (ts.incrementToken()) {}
                    ts.end();
                  }
                } catch (Throwable t) {
                  err.compareAndSet(null, t);
                } finally {
                  bDone.countDown();
                }
              });
      ta.start();
      tb.start();
      ta.join();
      tb.join();
      if (err.get() != null) {
        throw new AssertionError(err.get());
      }
      assertNotSame("each thread has its own components", aStream.get(), bStream.get());
      assertEquals(
          "thread A's token x|1.0 must keep payload 1.0", 1.0f, aPayload.get().floatValue(), 0f);
    }
  }

  private static int indexConcurrentlyAndCountWrong(Analyzer analyzer) throws Exception {
    final int threads = 4;
    final int perThread = 5000;
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        CyclicBarrier start = new CyclicBarrier(threads);
        AtomicReference<Throwable> err = new AtomicReference<>();
        Thread[] ts = new Thread[threads];
        for (int t = 0; t < threads; t++) {
          final int base = t * perThread;
          ts[t] =
              new Thread(
                  () -> {
                    try {
                      start.await();
                      for (int i = 0; i < perThread; i++) {
                        int id = base + i;
                        Document d = new Document();
                        d.add(new TextField("body", "t" + id + "|" + id + ".0", Field.Store.NO));
                        w.addDocument(d);
                      }
                    } catch (Throwable e) {
                      err.compareAndSet(null, e);
                    }
                  });
          ts[t].start();
        }
        for (Thread t : ts) {
          t.join();
        }
        if (err.get() != null) {
          throw new AssertionError(err.get());
        }
      }
      int wrong = 0;
      int checked = 0;
      String example = null;
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        for (LeafReaderContext ctx : r.leaves()) {
          Terms terms = ctx.reader().terms("body");
          TermsEnum te = terms.iterator();
          PostingsEnum pe = null;
          BytesRef term;
          while ((term = te.next()) != null) {
            int id = Integer.parseInt(term.utf8ToString().substring(1));
            pe = te.postings(pe, PostingsEnum.PAYLOADS);
            while (pe.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
              pe.nextPosition();
              BytesRef p = pe.getPayload();
              float got = PayloadHelper.decodeFloat(p.bytes, p.offset);
              checked++;
              if (got != (float) id) {
                wrong++;
                if (example == null) {
                  example = "term t" + id + " has payload " + got;
                }
              }
            }
          }
        }
      }
      assertEquals(threads * perThread, checked);
      if (wrong > 0) {
        System.out.println(
            "V1: wrong payloads " + wrong + " of " + checked + ", e.g. " + example);
      }
      return wrong;
    }
  }

  /** Real indexing path: 4 threads, one IndexWriter, analyzer built from the factory. */
  public void testConcurrentIndexingFactoryAnalyzer() throws Exception {
    try (Analyzer a = factoryAnalyzer(ENCODER)) {
      assertEquals("documents with another document's payload", 0, indexConcurrentlyAndCountWrong(a));
    }
  }

  /** Control: same encoder class, one instance per filter. Expected to pass. */
  public void testConcurrentIndexingPerFilterEncoderControl() throws Exception {
    try (Analyzer a = perFilterEncoderAnalyzer()) {
      assertEquals(0, indexConcurrentlyAndCountWrong(a));
    }
  }

  /** Control: built-in stateless float encoder through the factory. Expected to pass. */
  public void testConcurrentIndexingBuiltInFloatControl() throws Exception {
    try (Analyzer a = factoryAnalyzer("float")) {
      assertEquals(0, indexConcurrentlyAndCountWrong(a));
    }
  }
}
