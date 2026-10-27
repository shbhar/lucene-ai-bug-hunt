package org.apache.lucene.util.hnsw;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.search.TaskExecutor;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Follow-up: the wrong query vector on the retry not only corrupts stored scores, it also
 * changes which neighbors the inserted node keeps (the persisted graph topology).
 *
 * <p>Three production workers of one HnswConcurrentMergeBuilder (shared randSeed). Worker A
 * inserts node 3 (level 1); while A is linking level 0, workers B and C insert nodes 2 and 4
 * (both level 1), B wins the promotion. A's retry on level 1 then runs with the scorer pointed
 * at node 5 (the last level-0 candidate it re-pointed to) instead of node 3.
 */
public class TestCe1VerifyTopology extends LuceneTestCase {

  static final float[][] V = {
    {0f, 0f}, // 0  B, level 0, entry
    {10f, 0f}, // 1  A, level 0
    {1f, 3f}, // 2  B, level 1 (wins promotion)
    {1f, 0f}, // 3  A, level 1 (loses promotion, retries)
    {1f, -3f}, // 4  C, level 1
    {-10f, 0f}, // 5  C, level 0
  };

  static int[] sortedNbrs(OnHeapHnswGraph g, int level, int node) {
    NeighborArray na = g.getNeighbors(level, node);
    int[] out = Arrays.copyOf(na.nodes(), na.size());
    Arrays.sort(out);
    return out;
  }

  static OnHeapHnswGraph run(boolean forced) throws Exception {
    long saved = HnswGraphBuilder.randSeed;
    HnswGraphBuilder.randSeed = TestCe1VerifyRetryScorer.pickSeed();
    ExecutorService es = Executors.newFixedThreadPool(3);
    try {
      List<TestCe1VerifyRetryScorer.HookScorer> created = new ArrayList<>();
      OnHeapHnswGraph g = new OnHeapHnswGraph(TestCe1VerifyRetryScorer.M, V.length);
      HnswConcurrentMergeBuilder b =
          new HnswConcurrentMergeBuilder(
              new TaskExecutor(es),
              3,
              new TestCe1VerifyRetryScorer.HookSupplier(V, created),
              TestCe1VerifyRetryScorer.BEAM,
              g,
              null);
      HnswBuilder[] w = TestCe1VerifyRetryScorer.workers(b);
      HnswBuilder a = w[0], wb = w[1], wc = w[2];
      wb.addGraphNode(0);
      a.addGraphNode(1);
      wc.addGraphNode(5);
      if (forced) {
        AtomicReference<Throwable> err = new AtomicReference<>();
        TestCe1VerifyRetryScorer.HookScorer sa = created.get(0);
        sa.home = 3;
        sa.hook =
            () -> {
              for (int[] job : new int[][] {{1, 2}, {2, 4}}) {
                HnswBuilder wk = w[job[0]];
                Thread t =
                    new Thread(
                        () -> {
                          try {
                            wk.addGraphNode(job[1]);
                          } catch (Throwable e) {
                            err.set(e);
                          }
                        });
                t.start();
                try {
                  t.join();
                } catch (InterruptedException e) {
                  throw new RuntimeException(e);
                }
              }
            };
        Thread ta =
            new Thread(
                () -> {
                  try {
                    a.addGraphNode(3);
                  } catch (Throwable e) {
                    err.set(e);
                  }
                });
        ta.start();
        ta.join();
        if (err.get() != null) throw new AssertionError(err.get());
      } else {
        wb.addGraphNode(2);
        wc.addGraphNode(4);
        a.addGraphNode(3);
      }
      System.out.println(
          (forced ? "FORCED" : "SEQUENTIAL")
              + ": entry="
              + g.entryNode()
              + " levels(2,3,4)="
              + TestCe1VerifyRetryScorer.topLevel(g, 2)
              + ","
              + TestCe1VerifyRetryScorer.topLevel(g, 3)
              + ","
              + TestCe1VerifyRetryScorer.topLevel(g, 4)
              + " level-1 nbrs: 2="
              + Arrays.toString(sortedNbrs(g, 1, 2))
              + " 3="
              + Arrays.toString(sortedNbrs(g, 1, 3))
              + " 4="
              + Arrays.toString(sortedNbrs(g, 1, 4)));
      return g;
    } finally {
      HnswGraphBuilder.randSeed = saved;
      es.shutdown();
    }
  }

  public void testRetryWithWrongVectorChangesNeighborsOfInsertedNode() throws Exception {
    OnHeapHnswGraph control = run(false);
    OnHeapHnswGraph forced = run(true);
    assertEquals(1, TestCe1VerifyRetryScorer.topLevel(forced, 3));
    assertEquals(2, forced.entryNode());
    // diversity-pruned neighbors of node 3 on level 1 computed with node 3's own vector
    assertArrayEquals(new int[] {2, 4}, sortedNbrs(control, 1, 3));
    assertArrayEquals(
        "level-1 neighbors of node 3 after the lost-promotion retry",
        sortedNbrs(control, 1, 3),
        sortedNbrs(forced, 1, 3));
  }
}
