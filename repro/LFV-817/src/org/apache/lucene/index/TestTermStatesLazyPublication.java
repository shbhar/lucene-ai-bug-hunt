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
package org.apache.lucene.index;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.IOSupplier;

/**
 * Partitions of one leaf (intra-segment concurrency) share one TermStates per term. When scores
 * are not needed the TermStates is lazy (TermStates#build(searcher, term, false)), and
 * TermStates#get stores a freshly cloned TermState into a plain array slot that the other
 * partitions read with plain loads. Without a happens-before edge a partition can get the
 * published TermState before all of its fields are visible. This test lets 4 threads call
 * TermStates#get on the same fresh lazy TermStates objects at the same time, as 4 partitions of a
 * leaf do, snapshots each returned TermState immediately (clone) and then checks the snapshot the
 * way TermQuery/PhraseQuery consume it: seekExact(term, state), docFreq, totalTermFreq, postings.
 */
public class TestTermStatesLazyPublication extends LuceneTestCase {

  private static final int THREADS = 4;
  private static final int STRIDE = 256;

  public void testPartitionsSeeCompleteTermState() throws Exception {
    int strides = Integer.getInteger("h292.strides", 20000);
    try (Directory dir = FSDirectory.open(createTempDir("h292"))) {
      IndexWriterConfig iwc =
          new IndexWriterConfig(new StandardAnalyzer()).setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < 4000; i++) {
          Document d = new Document();
          d.add(
              new TextField(
                  "f",
                  String.format(Locale.ROOT, "u%05d x%04d y%03d", i, i % 1000, i % 7),
                  Field.Store.NO));
          w.addDocument(d);
        }
        w.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(1, reader.leaves().size());
        IndexSearcher searcher = new IndexSearcher(reader);
        LeafReaderContext ctx = reader.leaves().get(0);
        Term term = new Term("f", "x0777");
        List<Integer> expectedDocs = docs(ctx, term, null);
        assertEquals(List.of(777, 1777, 2777, 3777), expectedDocs);

        TermStates[] stride = new TermStates[STRIDE];
        AtomicInteger arrived = new AtomicInteger();
        AtomicInteger epoch = new AtomicInteger();
        AtomicLong checked = new AtomicLong();
        AtomicLong bad = new AtomicLong();
        AtomicLong shared = new AtomicLong(); // lookups that returned another thread's TermState
        TermState[][] got = new TermState[THREADS][STRIDE];
        AtomicInteger nextId = new AtomicInteger();
        AtomicReference<String> firstBad = new AtomicReference<>();
        AtomicReference<Throwable> crash = new AtomicReference<>();
        Runnable refill =
            () -> {
              for (int i = 0; i < STRIDE; i++) {
                try {
                  // what TermQuery#createWeight does when scores are not needed
                  stride[i] = TermStates.build(searcher, term, false);
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
              }
            };
        refill.run();
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < THREADS; t++) {
          Thread th =
              new Thread(
                  () -> {
                    int tid = nextId.getAndIncrement();
                    TermState[] snaps = new TermState[STRIDE];
                    try {
                      for (int s = 0; s < strides; s++) {
                        await(arrived, epoch, null);
                        for (int i = 0; i < STRIDE; i++) {
                          IOSupplier<TermState> sup = stride[i].get(ctx);
                          TermState ts = sup == null ? null : sup.get();
                          snaps[i] = ts == null ? null : ts.clone(); // read the fields now
                          got[tid][i] = ts;
                        }
                        for (int i = 0; i < STRIDE; i++) {
                          checked.incrementAndGet();
                          String why = check(ctx, term, snaps[i], expectedDocs);
                          if (why != null) {
                            bad.incrementAndGet();
                            firstBad.compareAndSet(null, why);
                          }
                        }
                        await(arrived, epoch, null);
                        if (tid == 0) {
                          for (int i = 0; i < STRIDE; i++) {
                            for (int u = 1; u < THREADS; u++) {
                              if (got[u][i] == got[0][i]) shared.incrementAndGet();
                            }
                          }
                        }
                        await(arrived, epoch, refill);
                      }
                    } catch (Throwable e) {
                      crash.compareAndSet(null, e);
                      epoch.set(Integer.MIN_VALUE); // release the others
                    }
                  });
          threads.add(th);
          th.start();
        }
        for (Thread th : threads) th.join();
        if (crash.get() != null) throw new AssertionError(crash.get());
        System.out.println(
            "lookups="
                + checked.get()
                + " sharedWithThread0="
                + shared.get()
                + " incomplete="
                + bad.get());
        assertEquals(
            "TermStates#get returned an incomplete TermState in "
                + bad.get()
                + " of "
                + checked.get()
                + " concurrent lookups; first: "
                + firstBad.get(),
            0,
            bad.get());
      }
    }
  }

  /** Spin barrier for THREADS threads; the last arriver runs action (if any) before release. */
  private static void await(AtomicInteger arrived, AtomicInteger epoch, Runnable action) {
    int e = epoch.get();
    if (e == Integer.MIN_VALUE) throw new IllegalStateException("aborted");
    if (arrived.incrementAndGet() == THREADS) {
      arrived.set(0);
      if (action != null) action.run();
      epoch.incrementAndGet();
    } else {
      while (epoch.get() == e) Thread.onSpinWait();
    }
  }

  private static String check(LeafReaderContext ctx, Term term, TermState snap, List<Integer> exp) {
    if (snap == null) return "null TermState for an existing term";
    try {
      TermsEnum te = ctx.reader().terms(term.field()).iterator();
      te.seekExact(term.bytes(), snap);
      if (te.docFreq() != exp.size() || te.totalTermFreq() != exp.size()) {
        return "docFreq=" + te.docFreq() + " totalTermFreq=" + te.totalTermFreq() + " state=" + snap;
      }
      List<Integer> got = docs(ctx, term, te);
      if (!got.equals(exp)) return "postings " + got + " state=" + snap;
      return null;
    } catch (Throwable t) {
      return t + " state=" + snap;
    }
  }

  private static List<Integer> docs(LeafReaderContext ctx, Term term, TermsEnum te)
      throws IOException {
    if (te == null) {
      te = ctx.reader().terms(term.field()).iterator();
      assertTrue(te.seekExact(term.bytes()));
    }
    PostingsEnum pe = te.postings(null, PostingsEnum.NONE);
    List<Integer> out = new ArrayList<>();
    for (int d = pe.nextDoc(); d != DocIdSetIterator.NO_MORE_DOCS; d = pe.nextDoc()) out.add(d);
    return out;
  }
}
