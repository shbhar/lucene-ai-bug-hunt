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

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * LUCENE-10428 (CHANGES 9.1.0: "Queries with a misbehaving score function may no longer cause
 * infinite loops in their parent BooleanQuery") turned the non-converging ulp loop of
 * MaxScoreSumPropagator into an IllegalStateException. ScorerUtil.minRequiredScore (#14827) has
 * the same kind of loop without that guard: for a negative maxRemainingScore and a
 * minCompetitiveScore of 0 it subtracts Math.ulp(0f) = Float.MIN_VALUE, which does not change the
 * double, so the loop never ends. MaxScoreBulkScorer reaches it from a top-k disjunction whose
 * non-essential clause reports a negative max score.
 *
 * <p>testMinRequiredScore* call the package-private method, as TestScorerUtil does; the
 * BooleanQuery test uses public API only. The searches run on a plain IndexSearcher (no executor,
 * default similarity), so the result does not depend on the random seed.
 */
// a call that never returns leaves its thread spinning; the test reports that itself
@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
public class TestMinRequiredScoreTermination extends LuceneTestCase {

  /** Runs the task on a daemon thread; fails if it neither returns nor throws within 20 s. */
  private static <T> T returnsOrThrows(String what, Callable<T> task) throws Exception {
    Thread[] worker = new Thread[1];
    ExecutorService executor =
        Executors.newSingleThreadExecutor(
            r -> {
              Thread t = new Thread(r, "task");
              t.setDaemon(true);
              worker[0] = t;
              return t;
            });
    try {
      Future<T> f = executor.submit(task);
      try {
        return f.get(20, TimeUnit.SECONDS);
      } catch (ExecutionException e) {
        // an exception is an acceptable outcome for a misbehaving scorer (LUCENE-10428)
        return null;
      } catch (TimeoutException e) {
        StringBuilder stack = new StringBuilder();
        StackTraceElement[] frames = worker[0].getStackTrace();
        for (int i = 0; i < Math.min(8, frames.length); i++) {
          stack.append("\n    at ").append(frames[i]);
        }
        throw new AssertionError(what + " did not return within 20 s; the thread is at:" + stack);
      }
    } finally {
      executor.shutdown();
    }
  }

  /** The values MaxScoreBulkScorer passes for the 2-clause repro below. */
  public void testMinRequiredScoreTerminatesAtZeroMinCompetitiveScore() throws Exception {
    returnsOrThrows(
        "ScorerUtil.minRequiredScore(-1.0, 0f, 2)", () -> ScorerUtil.minRequiredScore(-1.0, 0f, 2));
  }

  /**
   * A positive minCompetitiveScore does not help once |maxRemainingScore| is large enough that
   * Math.ulp(minCompetitiveScore) is below half an ulp of minCompetitiveScore - maxRemainingScore.
   */
  public void testMinRequiredScoreTerminatesAtPositiveMinCompetitiveScore() throws Exception {
    returnsOrThrows(
        "ScorerUtil.minRequiredScore(-3e9, 1f, 2)", () -> ScorerUtil.minRequiredScore(-3e9, 1f, 2));
  }

  /**
   * A query whose score function misbehaves (every match scores -1, and getMaxScore says so),
   * OR-ed with MatchAllDocsQuery: the top-k search must return or fail, not spin forever.
   */
  public void testBooleanQueryWithMisbehavingClauseTerminates() throws Exception {
    searchMisbehavingDisjunction(false);
  }

  /**
   * IndexSearcher#setTimeout does not help: the loop runs inside a single BulkScorer#score call, so
   * the timeout is never checked again.
   */
  public void testBooleanQueryWithMisbehavingClauseStopsAtSearchTimeout() throws Exception {
    searchMisbehavingDisjunction(true);
  }

  /**
   * The same loop through an in-tree query and no custom code: RescoreTopNQuery passes the values of
   * its DoubleValuesSource through as scores (and as its max score), here a constant -1.
   */
  public void testBooleanQueryWithNegativeRescoreTopNQueryTerminates() throws Exception {
    Directory dir = new ByteBuffersDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (int i = 0; i < 10; i++) {
        w.addDocument(new Document());
      }
    }
    try (DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      @SuppressWarnings("deprecation")
      Query matchAll = new MatchAllDocsQuery();
      Query q =
          new BooleanQuery.Builder()
              .add(
                  new RescoreTopNQuery(matchAll, DoubleValuesSource.constant(-1), 3),
                  BooleanClause.Occur.SHOULD)
              .add(matchAll, BooleanClause.Occur.SHOULD)
              .build();
      returnsOrThrows("IndexSearcher.search(q, 10) with RescoreTopNQuery", () -> searcher.search(q, 10));
    }
    dir.close();
  }

  private static void searchMisbehavingDisjunction(boolean withTimeout) throws Exception {
    Directory dir = new ByteBuffersDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (int i = 0; i < 10; i++) {
        w.addDocument(new Document());
      }
    }
    try (DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      if (withTimeout) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(500);
        searcher.setTimeout(() -> System.nanoTime() - deadline > 0);
      }
      @SuppressWarnings("deprecation")
      Query matchAll = new MatchAllDocsQuery();
      Query q =
          new BooleanQuery.Builder()
              .add(new NegativeScoreQuery(-1f), BooleanClause.Occur.SHOULD)
              .add(matchAll, BooleanClause.Occur.SHOULD)
              .build();
      returnsOrThrows(
          "IndexSearcher.search(q, 10)" + (withTimeout ? " with a 500 ms search timeout" : ""),
          () -> searcher.search(q, 10));
    }
    dir.close();
  }

  /** Matches every document with the given (negative) score, and reports it as its max score. */
  private static final class NegativeScoreQuery extends Query {
    private final float score;

    NegativeScoreQuery(float score) {
      this.score = score;
    }

    @Override
    public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) {
      return new Weight(this) {
        @Override
        public ScorerSupplier scorerSupplier(LeafReaderContext context) {
          DocIdSetIterator it = DocIdSetIterator.all(context.reader().maxDoc());
          Scorer scorer =
              new Scorer() {
                @Override
                public int docID() {
                  return it.docID();
                }

                @Override
                public DocIdSetIterator iterator() {
                  return it;
                }

                @Override
                public float score() {
                  return score;
                }

                @Override
                public float getMaxScore(int upTo) {
                  return score;
                }
              };
          return new DefaultScorerSupplier(scorer);
        }

        @Override
        public Explanation explain(LeafReaderContext context, int doc) throws IOException {
          return Explanation.match(score, "negative score");
        }

        @Override
        public boolean isCacheable(LeafReaderContext ctx) {
          return false;
        }
      };
    }

    @Override
    public String toString(String field) {
      return "NegativeScoreQuery(" + score + ")";
    }

    @Override
    public void visit(QueryVisitor visitor) {
      visitor.visitLeaf(this);
    }

    @Override
    public boolean equals(Object other) {
      return sameClassAs(other) && score == ((NegativeScoreQuery) other).score;
    }

    @Override
    public int hashCode() {
      return 31 * classHash() + Float.hashCode(score);
    }
  }
}
