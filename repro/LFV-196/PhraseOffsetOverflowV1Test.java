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
package verify.phraseoverflow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MultiPhraseQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.junit.Test;

/**
 * Independent verifier repro (public API only, no randomization).
 *
 * <p>PhraseQuery.Builder#add(Term,int) accepts any position >= 0 that is not smaller than the
 * previous one, and MultiPhraseQuery.Builder#add(Term[],int) accepts any int position (upstream
 * tests use -1). No document position can exceed IndexWriter.MAX_POSITION = Integer.MAX_VALUE - 128.
 * So a phrase whose terms are about 2^31 positions apart can never be within a small slop of any
 * document. Each query below must therefore return 0 hits, and must return at all.
 *
 * <p>Searches that might not return run on a daemon thread with a bounded wait so that a hang shows
 * as a test failure and does not keep the JVM alive.
 */
public class PhraseOffsetOverflowV1Test {

  private static final String F = "f";
  private static final long WAIT_SECONDS = Long.getLong("v1.waitSeconds", 5);

  private static Term t(String text) {
    return new Term(F, text);
  }

  private static String repeat(String word, int n) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < n; i++) {
      sb.append(word).append(' ');
    }
    return sb.toString();
  }

  /** Index one document with the given whitespace-separated text and run the action on it. */
  private static <T> T withSingleDoc(String text, SearchAction<T> action) throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
        Document doc = new Document();
        doc.add(new TextField(F, text, Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        return action.run(new IndexSearcher(r));
      }
    }
  }

  interface SearchAction<T> {
    T run(IndexSearcher searcher) throws Exception;
  }

  /** Run on a daemon thread; fail if it does not finish within WAIT_SECONDS. */
  private static <T> T bounded(String what, Callable<T> c) throws Exception {
    final java.util.concurrent.atomic.AtomicReference<Thread> worker =
        new java.util.concurrent.atomic.AtomicReference<>();
    ExecutorService ex =
        Executors.newSingleThreadExecutor(
            r -> {
              Thread th = new Thread(r, "bounded-search");
              th.setDaemon(true);
              worker.set(th);
              return th;
            });
    try {
      Future<T> f = ex.submit(c);
      try {
        return f.get(WAIT_SECONDS, TimeUnit.SECONDS);
      } catch (TimeoutException e) {
        // Two stack samples of the still-running search thread, 200 ms apart.
        String s1 = topFrames(worker.get());
        Thread.sleep(200);
        String s2 = topFrames(worker.get());
        f.cancel(true); // has no effect on a busy loop, but harmless
        fail(
            what
                + " did not return within "
                + WAIT_SECONDS
                + " s; thread state="
                + worker.get().getState()
                + "; sample1="
                + s1
                + "; sample2="
                + s2);
        return null;
      }
    } finally {
      ex.shutdownNow();
    }
  }

  private static String topFrames(Thread th) {
    StringBuilder sb = new StringBuilder("[");
    StackTraceElement[] st = th.getStackTrace();
    for (int i = 0; i < Math.min(3, st.length); i++) {
      if (i > 0) {
        sb.append(" <- ");
      }
      sb.append(st[i].getClassName().replace("org.apache.lucene.search.", ""))
          .append('.')
          .append(st[i].getMethodName());
    }
    boolean timeLimited = false;
    for (StackTraceElement e : st) {
      timeLimited |= e.getClassName().endsWith("TimeLimitingBulkScorer");
    }
    return sb.append("] TimeLimitingBulkScorer-on-stack=").append(timeLimited).toString();
  }

  private static int boundedCount(String text, Query q) throws Exception {
    return withSingleDoc(text, s -> bounded("IndexSearcher#count(" + desc(q) + ")", () -> s.count(q)));
  }

  /** A short description that does not call Query#toString (which has its own problem here). */
  private static String desc(Query q) {
    if (q instanceof PhraseQuery pq) {
      return "PhraseQuery positions="
          + java.util.Arrays.toString(pq.getPositions())
          + " slop="
          + pq.getSlop();
    } else if (q instanceof MultiPhraseQuery mpq) {
      return "MultiPhraseQuery positions="
          + java.util.Arrays.toString(mpq.getPositions())
          + " slop="
          + mpq.getSlop();
    }
    return q.getClass().getSimpleName();
  }

  private static PhraseQuery phrase(int slop, String t1, int p1, String t2, int p2) {
    return new PhraseQuery.Builder().setSlop(slop).add(t(t1), p1).add(t(t2), p2).build();
  }

  // ---------------------------------------------------------------------------------------------
  // Exact phrase (slop 0): ExactPhraseMatcher computes phrasePos + offset in int.

  /** Bug: a@0 b@Integer.MAX_VALUE on "x a b" (lead term at position 1) must return 0 hits. */
  @Test
  public void testExactOffsetMaxValueLeadAtPos1() throws Exception {
    assertEquals(0, boundedCount("x a b", phrase(0, "a", 0, "b", Integer.MAX_VALUE)));
  }

  /** Control: same query, lead term at position 0, no overflow: returns 0 promptly. */
  @Test
  public void testControlExactOffsetMaxValueLeadAtPos0() throws Exception {
    assertEquals(0, boundedCount("a b", phrase(0, "a", 0, "b", Integer.MAX_VALUE)));
  }

  /** Control: a normal phrase on the same document matches (the test machinery works). */
  @Test
  public void testControlExactNormalPhraseMatches() throws Exception {
    assertEquals(1, boundedCount("x a b", phrase(0, "a", 0, "b", 1)));
  }

  /**
   * Bug at the documented document-position limit: offset IndexWriter.MAX_POSITION with the lead
   * at document position 129 (129 + MAX_POSITION = Integer.MAX_VALUE + 1).
   */
  @Test
  public void testExactOffsetMaxPositionLeadAtPos129() throws Exception {
    assertEquals(
        0,
        boundedCount(repeat("x", 129) + "a b", phrase(0, "a", 0, "b", IndexWriter.MAX_POSITION)));
  }

  /** Control: lead at 128 (128 + MAX_POSITION = Integer.MAX_VALUE, no overflow) returns 0. */
  @Test
  public void testControlExactOffsetMaxPositionLeadAtPos128() throws Exception {
    assertEquals(
        0,
        boundedCount(repeat("x", 128) + "a b", phrase(0, "a", 0, "b", IndexWriter.MAX_POSITION)));
  }

  /** Same root cause through MultiPhraseQuery (slop 0 also uses ExactPhraseMatcher). */
  @Test
  public void testExactMultiPhraseOffsetMaxValueLeadAtPos1() throws Exception {
    MultiPhraseQuery q =
        new MultiPhraseQuery.Builder()
            .add(new Term[] {t("a")}, 0)
            .add(new Term[] {t("b")}, Integer.MAX_VALUE)
            .build();
    assertEquals(0, boundedCount("x a b", q));
  }

  /**
   * IndexSearcher#setTimeout with a QueryTimeout that expires after 1 s: the search should come
   * back (possibly with partial results) well within 5 s. Observed: it does not, because the loop
   * runs inside one document's TwoPhaseIterator#matches.
   */
  @Test
  public void testExactLoopNotStoppedBySetTimeout() throws Exception {
    PhraseQuery q = phrase(0, "a", 0, "b", Integer.MAX_VALUE);
    TopDocs td =
        withSingleDoc(
            "x a b",
            s -> {
              final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
              s.setTimeout(() -> System.nanoTime() > deadline);
              return bounded("IndexSearcher#search(" + desc(q) + ", 10) with a 1 s QueryTimeout",
                  () -> s.search(q, 10));
            });
    assertEquals(0, td.scoreDocs.length);
  }

  // ---------------------------------------------------------------------------------------------
  // Sloppy phrase: SloppyPhraseMatcher computes end - pp.position in int.

  /**
   * Bug: a@0 b@Integer.MAX_VALUE slop 1 on "b x x a". Query wants b about 2^31 after a; document has
   * b 3 before a. The edit distance is about 2^31 > 1, so no match.
   */
  @Test
  public void testSloppyOffsetMaxValueFalseMatch() throws Exception {
    assertEquals(0, boundedCount("b x x a", phrase(1, "a", 0, "b", Integer.MAX_VALUE)));
  }

  /** Control: a@0 b@5 slop 1 on the same document (edit distance 8) does not match. */
  @Test
  public void testControlSloppySmallOffsetNoMatch() throws Exception {
    assertEquals(0, boundedCount("b x x a", phrase(1, "a", 0, "b", 5)));
  }

  /** Control: a@0 b@3 slop 1 on "a x x b" (exact distance) matches. */
  @Test
  public void testControlSloppyMatches() throws Exception {
    assertEquals(1, boundedCount("a x x b", phrase(1, "a", 0, "b", 3)));
  }

  /**
   * Bug through MultiPhraseQuery, whose builder accepts negative positions (upstream
   * TestMultiPhraseQuery uses -1): a@Integer.MIN_VALUE b@0 slop 1 on "a x x b". Query wants b 2^31
   * after a; document has it 3 after a: no match.
   */
  @Test
  public void testSloppyMultiPhraseMinValueFalseMatch() throws Exception {
    MultiPhraseQuery q =
        new MultiPhraseQuery.Builder()
            .setSlop(1)
            .add(new Term[] {t("a")}, Integer.MIN_VALUE)
            .add(new Term[] {t("b")}, 0)
            .build();
    assertEquals(0, boundedCount("a x x b", q));
  }

  /** Control: a@-1 b@0 slop 1 on "a x x b" (edit distance 2) does not match; slop 2 does. */
  @Test
  public void testControlSloppyMultiPhraseSmallNegative() throws Exception {
    MultiPhraseQuery.Builder b =
        new MultiPhraseQuery.Builder().add(new Term[] {t("a")}, -1).add(new Term[] {t("b")}, 0);
    assertEquals(0, boundedCount("a x x b", b.setSlop(1).build()));
    assertEquals(1, boundedCount("a x x b", b.setSlop(2).build()));
  }

  // ---------------------------------------------------------------------------------------------
  // Side observation (different code): PhraseQuery#toString sizes an array by the max position.

  @Test
  public void testToStringOfLegalQuery() throws Exception {
    PhraseQuery q = phrase(0, "a", 0, "b", Integer.MAX_VALUE);
    String s = bounded("PhraseQuery#toString", () -> q.toString());
    // Any non-throwing rendering is acceptable here; the point is that it must not throw.
    if (s == null) {
      fail("toString returned null");
    }
  }
}
