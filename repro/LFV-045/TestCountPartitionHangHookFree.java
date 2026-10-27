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
package org.apache.lucene.search;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.ExitableDirectoryReader;
import org.apache.lucene.index.ExitableDirectoryReader.ExitingReaderException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.QueryTimeoutImpl;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.LuceneTestCase.SuppressSysoutChecks;

/**
 * Hook-free check: {@link IndexSearcher#count(Query)} on a searcher with an executor, over one
 * segment that the documented opt-in {@link IndexSearcher#slices(List, int, int, boolean)} splits
 * into two intra-segment partitions, must throw when {@link Weight#count} throws. It must not hang.
 *
 * <p>No test hooks: no latches, no blocking callbacks, and no {@link IndexSearcher} override other
 * than {@code slices(List)}, the opt-in the javadoc and CHANGES name. Trigger 1 uses only stock
 * classes: a {@link TermQuery} over an {@link ExitableDirectoryReader} with a {@link
 * QueryTimeoutImpl} whose budget is already spent. Trigger 2 is a query whose {@link Weight#count}
 * throws its declared {@link IOException} at once.
 *
 * <p>Each trial uses a fresh 1-thread pool whose thread is already started and idle, as in a
 * long-lived search pool, so the caller thread and one pool thread run the two partition tasks. A
 * call still running after the bound is counted as a hang only if, at the bound and again later,
 * one of the two threads is parked in the partition-aware count collector's {@code
 * getLeafCollector} and the other is either idle or in {@code TaskExecutor.collectResults}.
 */
@SuppressSysoutChecks(bugUrl = "prints one outcome summary per test")
public class TestCountPartitionHangHookFree extends LuceneTestCase {

  private static final int NUM_DOCS = 2000;
  private static final int MAX_DOCS_PER_SLICE = 1000; // the 2000-doc segment becomes 2 partitions
  private static final int TRIALS = Integer.getInteger("hf.trials", 16);
  private static final long BOUND_MS = 1000; // a count that throws returns in far less than this
  private static final long RECHECK_MS = 500; // a hang must still be parked this much later
  private static final long FIRST_HANG_WATCH_MS = 5000; // the first hang is watched for longer

  private static final String PARTITION_COLLECTOR =
      "org.apache.lucene.search.TotalHitCountCollectorManager$LeafPartitionAwareTotalHitCountCollector";

  private Directory dir;
  private DirectoryReader reader;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = new ByteBuffersDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (int i = 0; i < NUM_DOCS; i++) {
        Document doc = new Document();
        doc.add(new StringField("f", "a", Field.Store.NO));
        w.addDocument(doc);
      }
      w.forceMerge(1);
    }
    reader = DirectoryReader.open(dir);
    assertEquals(1, reader.leaves().size());
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  // ---- trigger 1: stock TermQuery, stock ExitableDirectoryReader, budget already spent

  public void testTimeoutControlWholeSegment() throws Exception {
    IndexReader r = new ExitableDirectoryReader(reader, new QueryTimeoutImpl(0));
    run(r, false, () -> new TermQuery(new Term("f", "a")), ExitingReaderException.class)
        .assertEveryTrialThrewPromptly();
  }

  public void testTimeoutPartitionedSegment() throws Exception {
    IndexReader r = new ExitableDirectoryReader(reader, new QueryTimeoutImpl(0));
    run(r, true, () -> new TermQuery(new Term("f", "a")), ExitingReaderException.class)
        .assertEveryTrialThrewPromptly();
  }

  // ---- trigger 2: Weight#count throws its declared IOException at once

  public void testIOExceptionControlWholeSegment() throws Exception {
    run(reader, false, CountThrowsQuery::new, IOException.class).assertEveryTrialThrewPromptly();
  }

  public void testIOExceptionPartitionedSegment() throws Exception {
    run(reader, true, CountThrowsQuery::new, IOException.class).assertEveryTrialThrewPromptly();
  }

  // ---- driver

  private Outcomes run(
      IndexReader r, boolean partitions, Supplier<Query> query, Class<? extends Throwable> expected)
      throws Exception {
    Outcomes out = new Outcomes(getTestName(), partitions, expected);
    for (int trial = 0; trial < TRIALS; trial++) {
      final String suffix = "-" + trial;
      List<Thread> poolThreads = new CopyOnWriteArrayList<>();
      ThreadPoolExecutor pool =
          new ThreadPoolExecutor(
              1,
              1,
              0L,
              TimeUnit.MILLISECONDS,
              new LinkedBlockingQueue<>(),
              task -> {
                Thread t = new Thread(task, "hf-pool" + suffix);
                t.setDaemon(true);
                poolThreads.add(t);
                return t;
              });
      try {
        pool.prestartAllCoreThreads();
        awaitIdle(poolThreads);
        IndexSearcher searcher =
            new IndexSearcher(r, pool) {
              @Override
              protected LeafSlice[] slices(List<LeafReaderContext> leaves) {
                return slices(leaves, MAX_DOCS_PER_SLICE, 1, partitions);
              }
            };
        assertEquals(partitions ? 2 : 1, searcher.getSlices().length);
        Query q = query.get();
        FutureTask<Integer> call = new FutureTask<>(() -> searcher.count(q));
        Thread caller = new Thread(call, "hf-caller" + suffix);
        caller.setDaemon(true);
        caller.start();
        try {
          out.returned(trial, call.get(BOUND_MS, TimeUnit.MILLISECONDS));
        } catch (ExecutionException e) {
          out.threw(trial, e.getCause());
        } catch (TimeoutException _) {
          String atBound = parkedState(caller, poolThreads);
          long watch = out.hangs.isEmpty() ? FIRST_HANG_WATCH_MS : RECHECK_MS;
          Thread.sleep(watch);
          String later = parkedState(caller, poolThreads);
          out.hang(
              trial, call.isDone(), atBound, later, BOUND_MS + watch, stacks(caller, poolThreads));
          // unpark both threads so that the next trial starts clean
          pool.shutdownNow();
          caller.interrupt();
          caller.join(TimeUnit.SECONDS.toMillis(5));
        }
      } finally {
        pool.shutdownNow();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
      }
    }
    System.out.println(out);
    return out;
  }

  /** Waits until every pool thread is parked waiting for work (the steady state of a pool). */
  private static void awaitIdle(List<Thread> poolThreads) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    for (Thread t : poolThreads) {
      while (t.getState() != Thread.State.WAITING) {
        if (System.nanoTime() > deadline) {
          throw new AssertionError("test setup: pool thread not idle: " + t.getState());
        }
        Thread.onSpinWait();
      }
    }
  }

  /** "waiter=caller", "waiter=pool" or "unclassified: ..." for a call that has not returned. */
  private static String parkedState(Thread caller, List<Thread> poolThreads) {
    boolean callerWaits = waitsInPartitionCollector(caller);
    boolean callerCollects =
        inFrame(caller, "org.apache.lucene.search.TaskExecutor", "collectResults");
    boolean poolWaits = false;
    boolean poolInLucene = false;
    for (Thread t : poolThreads) {
      poolWaits |= waitsInPartitionCollector(t);
      poolInLucene |= inPackage(t, "org.apache.lucene.");
    }
    if (callerWaits && poolInLucene == false) {
      return "waiter=caller (pool thread idle)";
    }
    if (poolWaits && callerCollects) {
      return "waiter=pool (caller in TaskExecutor.collectResults)";
    }
    return "unclassified: callerWaits="
        + callerWaits
        + " callerCollects="
        + callerCollects
        + " poolWaits="
        + poolWaits
        + " poolInLucene="
        + poolInLucene;
  }

  private static boolean waitsInPartitionCollector(Thread t) {
    return t.getState() == Thread.State.WAITING
        && inFrame(t, "java.util.concurrent.CompletableFuture", "get")
        && inFrame(t, PARTITION_COLLECTOR, "getLeafCollector");
  }

  private static boolean inFrame(Thread t, String className, String method) {
    for (StackTraceElement e : t.getStackTrace()) {
      if (e.getClassName().equals(className) && e.getMethodName().equals(method)) {
        return true;
      }
    }
    return false;
  }

  private static boolean inPackage(Thread t, String prefix) {
    for (StackTraceElement e : t.getStackTrace()) {
      if (e.getClassName().startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  private static String stacks(Thread caller, List<Thread> poolThreads) {
    StringBuilder sb = new StringBuilder(stack(caller));
    for (Thread t : poolThreads) {
      sb.append(stack(t));
    }
    return sb.toString();
  }

  private static String stack(Thread t) {
    StringBuilder sb = new StringBuilder();
    sb.append("  \"").append(t.getName()).append("\" ").append(t.getState()).append('\n');
    StackTraceElement[] frames = t.getStackTrace();
    for (int i = 0; i < Math.min(frames.length, 14); i++) {
      sb.append("      at ").append(frames[i]).append('\n');
    }
    return sb.toString();
  }

  /** True if {@code t}, its causes or their suppressed exceptions include a {@code type}. */
  private static boolean carries(Throwable t, Class<? extends Throwable> type) {
    return carries(t, type, Collections.newSetFromMap(new IdentityHashMap<>()));
  }

  private static boolean carries(
      Throwable t, Class<? extends Throwable> type, Set<Throwable> seen) {
    if (t == null || seen.add(t) == false) {
      return false;
    }
    if (type.isInstance(t) || carries(t.getCause(), type, seen)) {
      return true;
    }
    for (Throwable s : t.getSuppressed()) {
      if (carries(s, type, seen)) {
        return true;
      }
    }
    return false;
  }

  /** Outcome counts of one test's trials. */
  private static final class Outcomes {
    final String test;
    final boolean partitions;
    final Class<? extends Throwable> expected;
    int threwExpected;
    int threwExpectedTopLevel;
    final List<String> threwOther = new ArrayList<>();
    final List<String> returned = new ArrayList<>();
    final List<String> hangs = new ArrayList<>();
    int confirmedHangs;
    String firstHangStacks;

    Outcomes(String test, boolean partitions, Class<? extends Throwable> expected) {
      this.test = test;
      this.partitions = partitions;
      this.expected = expected;
    }

    void returned(int trial, Integer count) {
      returned.add("trial " + trial + ": returned " + count);
    }

    void threw(int trial, Throwable t) {
      if (carries(t, expected)) {
        threwExpected++;
        if (expected.isInstance(t)) {
          threwExpectedTopLevel++;
        }
      } else {
        threwOther.add("trial " + trial + ": " + t);
      }
    }

    void hang(
        int trial, boolean doneLater, String atBound, String later, long watchedMs, String stacks) {
      boolean confirmed =
          doneLater == false && atBound.equals(later) && atBound.startsWith("waiter=");
      if (confirmed) {
        confirmedHangs++;
      }
      if (firstHangStacks == null) {
        firstHangStacks = stacks;
      }
      hangs.add(
          "trial "
              + trial
              + ": not returned after "
              + watchedMs
              + " ms, "
              + atBound
              + (confirmed
                  ? " [confirmed]"
                  : " [state changed: " + later + ", done=" + doneLater + "]"));
    }

    void assertEveryTrialThrewPromptly() {
      if (hangs.isEmpty() == false
          || threwOther.isEmpty() == false
          || returned.isEmpty() == false) {
        throw new AssertionError(this.toString());
      }
      assertEquals(TRIALS, threwExpected);
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("SUMMARY ")
          .append(test)
          .append(" partitions=")
          .append(partitions)
          .append(" trials=")
          .append(TRIALS)
          .append(": threw ")
          .append(expected.getSimpleName())
          .append('=')
          .append(threwExpected)
          .append(" (top-level ")
          .append(threwExpectedTopLevel)
          .append("), hangs=")
          .append(hangs.size())
          .append(" (confirmed parked ")
          .append(confirmedHangs)
          .append("), other exception=")
          .append(threwOther.size())
          .append(", returned=")
          .append(returned.size())
          .append('\n');
      for (String h : hangs) {
        sb.append("  ").append(h).append('\n');
      }
      for (String o : threwOther) {
        sb.append("  ").append(o).append('\n');
      }
      for (String o : returned) {
        sb.append("  ").append(o).append('\n');
      }
      if (firstHangStacks != null) {
        sb.append("  stacks of the first hang:\n").append(firstHangStacks);
      }
      return sb.toString();
    }
  }

  /** A query whose Weight#count throws its declared IOException, like a read error would. */
  private static final class CountThrowsQuery extends Query {

    @Override
    public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) {
      return new ConstantScoreWeight(this, boost) {
        @Override
        public int count(LeafReaderContext context) throws IOException {
          throw new IOException("read error in Weight#count");
        }

        @Override
        public ScorerSupplier scorerSupplier(LeafReaderContext context) {
          throw new AssertionError("not reached: count() never returns -1");
        }

        @Override
        public boolean isCacheable(LeafReaderContext ctx) {
          return false;
        }
      };
    }

    @Override
    public String toString(String field) {
      return "CountThrowsQuery";
    }

    @Override
    public void visit(QueryVisitor visitor) {}

    @Override
    public boolean equals(Object other) {
      return this == other;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }
  }
}
