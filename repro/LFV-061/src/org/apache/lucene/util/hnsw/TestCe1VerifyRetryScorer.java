package org.apache.lucene.util.hnsw;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.search.TaskExecutor;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent verification of the finding: a worker of HnswConcurrentMergeBuilder that loses the
 * entry-node promotion race retries the newly introduced levels with its shared
 * UpdateableRandomVectorScorer still pointed at whatever ordinal selectAndLinkDiverse /
 * NeighborArray last set, not at the node being inserted.
 *
 * <p>The test drives the real production worker objects (ConcurrentMergeWorker, MergeSearcher,
 * HnswLock) of a real HnswConcurrentMergeBuilder, obtained via reflection. The interleaving is
 * forced with real threads: the first time worker A's scorer is re-pointed away from the node it
 * is inserting (that is inside selectAndLinkDiverse, where no HnswLock is held) worker A blocks
 * while worker B, on another thread, inserts a node that raises the graph level and wins the
 * promotion CAS.
 */
public class TestCe1VerifyRetryScorer extends LuceneTestCase {

  // 2-D vectors. Node 3 (inserted by worker A) and node 2 (inserted by worker B) both get the
  // same random level L >= 1 because both workers share HnswGraphBuilder.randSeed.
  static final float[][] V = {
    {0f, 0f}, // 0
    {10f, 0f}, // 1
    {0f, 5f}, // 2
    {1f, 0f}, // 3
  };

  static final int M = 2;
  static final int BEAM = 10;

  /** exact and symmetric similarity (same formula as EUCLIDEAN: 1 / (1 + squared distance)) */
  static float sim(float[][] v, int a, int b) {
    float d = 0;
    for (int i = 0; i < v[a].length; i++) {
      float x = v[a][i] - v[b][i];
      d += x * x;
    }
    return 1f / (1f + d);
  }

  /** A scorer with an optional one-shot hook fired on the first re-point away from "home". */
  static final class HookScorer implements UpdateableRandomVectorScorer {
    final float[][] v;
    final String name;
    int ord = -1;
    volatile int home = -1;
    volatile Runnable hook;
    final List<String> log = new ArrayList<>();

    HookScorer(float[][] v, String name) {
      this.v = v;
      this.name = name;
    }

    @Override
    public void setScoringOrdinal(int node) {
      log.add("setScoringOrdinal(" + node + ")");
      ord = node;
      Runnable h = hook;
      if (h != null && home >= 0 && node != home) {
        hook = null;
        StackTraceElement[] st = new Throwable().getStackTrace();
        log.add("HOOK fired at " + st[1] + " <- " + st[2]);
        h.run();
      }
    }

    @Override
    public float score(int node) {
      log.add("score(" + node + ") with ordinal " + ord);
      return sim(v, ord, node);
    }

    @Override
    public int maxOrd() {
      return v.length;
    }
  }

  static final class HookSupplier implements RandomVectorScorerSupplier {
    final float[][] v;
    final List<HookScorer> created;

    HookSupplier(float[][] v, List<HookScorer> created) {
      this.v = v;
      this.created = created;
    }

    @Override
    public UpdateableRandomVectorScorer scorer() {
      HookScorer s = new HookScorer(v, "scorer#" + created.size());
      created.add(s);
      return s;
    }

    @Override
    public RandomVectorScorerSupplier copy() {
      return new HookSupplier(v, created);
    }
  }

  // replicates HnswGraphBuilder.getRandomGraphLevel (private static)
  static int[] levels(long seed, int n, int m) {
    double ml = m == 1 ? 1 : 1 / Math.log(1.0 * m);
    SplittableRandom r = new SplittableRandom(seed);
    int[] out = new int[n];
    for (int i = 0; i < n; i++) {
      double d;
      do {
        d = r.nextDouble();
      } while (d == 0.0);
      out[i] = (int) (-Math.log(d) * ml);
    }
    return out;
  }

  static long pickSeed() {
    for (long s = 1; s < 10_000; s++) {
      int[] l = levels(s, 2, M);
      if (l[0] == 0 && l[1] >= 1) {
        return s;
      }
    }
    throw new AssertionError("no seed");
  }

  static final class Run {
    OnHeapHnswGraph graph;
    List<HookScorer> scorers;
    List<String> mismatches = new ArrayList<>();
    int nodeLevel3, nodeLevel2;
  }

  static HnswBuilder[] workers(HnswConcurrentMergeBuilder b) throws Exception {
    Field f = HnswConcurrentMergeBuilder.class.getDeclaredField("workers");
    f.setAccessible(true);
    Object[] w = (Object[]) f.get(b);
    HnswBuilder[] out = new HnswBuilder[w.length];
    for (int i = 0; i < w.length; i++) out[i] = (HnswBuilder) w[i];
    return out;
  }

  static int topLevel(OnHeapHnswGraph g, int node) {
    int l = 0;
    while (g.nodeExistAtLevel(l + 1, node)) l++;
    return l;
  }

  static void audit(Run run, float[][] v) {
    OnHeapHnswGraph g = run.graph;
    for (int level = 0; level < g.numLevels(); level++) {
      for (int n = 0; n < v.length; n++) {
        if (g.nodeExistAtLevel(level, n) == false) continue;
        NeighborArray na = g.getNeighbors(level, n);
        for (int i = 0; i < na.size(); i++) {
          int nb = na.nodes()[i];
          float stored = na.getScores(i);
          float truth = sim(v, n, nb);
          if (Float.isNaN(stored) == false && stored != truth) {
            run.mismatches.add(
                "level "
                    + level
                    + ": "
                    + n
                    + " -> "
                    + nb
                    + " stored="
                    + stored
                    + " true sim="
                    + truth);
          }
        }
      }
    }
  }

  /** forced=true: race forced; forced=false: same four insertions, sequential (control). */
  static Run runScenario(boolean forced) throws Exception {
    long savedSeed = HnswGraphBuilder.randSeed;
    long seed = pickSeed();
    HnswGraphBuilder.randSeed = seed;
    ExecutorService es = Executors.newFixedThreadPool(2);
    Run run = new Run();
    try {
      List<HookScorer> created = new ArrayList<>();
      OnHeapHnswGraph graph = new OnHeapHnswGraph(M, V.length);
      HnswConcurrentMergeBuilder b =
          new HnswConcurrentMergeBuilder(
              new TaskExecutor(es), 2, new HookSupplier(V, created), BEAM, graph, null);
      HnswBuilder[] w = workers(b);
      HnswBuilder a = w[0];
      HnswBuilder bb = w[1];
      HookScorer scorerA = created.get(0);
      assertEquals(2, created.size());

      bb.addGraphNode(0); // B draws level seq[0] = 0 -> entry node
      a.addGraphNode(1); // A draws level seq[0] = 0
      if (forced) {
        AtomicReference<Throwable> err = new AtomicReference<>();
        scorerA.home = 3;
        scorerA.hook =
            () -> {
              Thread tb =
                  new Thread(
                      () -> {
                        try {
                          bb.addGraphNode(2); // B draws seq[1] = L, promotes itself
                        } catch (Throwable t) {
                          err.set(t);
                        }
                      },
                      "worker-B");
              tb.start();
              try {
                tb.join();
              } catch (InterruptedException e) {
                throw new RuntimeException(e);
              }
              scorerA.log.add(
                  "  [worker-B finished: entry="
                      + graph.entryNode()
                      + " numLevels="
                      + graph.numLevels()
                      + "]");
            };
        Thread ta =
            new Thread(
                () -> {
                  try {
                    a.addGraphNode(3); // A draws seq[1] = L
                  } catch (Throwable t) {
                    err.set(t);
                  }
                },
                "worker-A");
        ta.start();
        ta.join();
        if (err.get() != null) throw new AssertionError(err.get());
      } else {
        bb.addGraphNode(2);
        a.addGraphNode(3);
      }
      run.graph = graph;
      run.scorers = created;
      run.nodeLevel2 = topLevel(graph, 2);
      run.nodeLevel3 = topLevel(graph, 3);
      audit(run, V);
      System.out.println(
          "--- "
              + (forced ? "FORCED race" : "SEQUENTIAL control")
              + " (randSeed="
              + seed
              + ", levels: node2="
              + run.nodeLevel2
              + " node3="
              + run.nodeLevel3
              + ", entry="
              + graph.entryNode()
              + ", numLevels="
              + graph.numLevels()
              + ")");
      if (forced) {
        System.out.println("worker A scorer trace while inserting node 3:");
        boolean on = false;
        for (String s : scorerA.log) {
          if (s.equals("setScoringOrdinal(3)")) on = true;
          if (on) System.out.println("  " + s);
        }
      }
      for (int level = 0; level < graph.numLevels(); level++) {
        for (int n = 0; n < V.length; n++) {
          if (graph.nodeExistAtLevel(level, n) == false) continue;
          NeighborArray na = graph.getNeighbors(level, n);
          StringBuilder sb = new StringBuilder();
          for (int i = 0; i < na.size(); i++) {
            sb.append(na.nodes()[i]).append('(').append(na.getScores(i)).append(") ");
          }
          System.out.println("  L" + level + " node " + n + ": " + sb);
        }
      }
      System.out.println("mismatches: " + run.mismatches.size());
      for (String s : run.mismatches) System.out.println("  " + s);
      return run;
    } finally {
      HnswGraphBuilder.randSeed = savedSeed;
      es.shutdown();
    }
  }

  public void testSequentialControlHasCorrectScores() throws Exception {
    Run r = runScenario(false);
    assertTrue("precondition: node 3 above level 0", r.nodeLevel3 >= 1);
    assertEquals("precondition: same level", r.nodeLevel2, r.nodeLevel3);
    assertEquals(r.mismatches.toString(), 0, r.mismatches.size());
  }

  public void testLostPromotionRetryStoresScoresOfOtherVector() throws Exception {
    Run r = runScenario(true);
    assertTrue("precondition: node 3 above level 0", r.nodeLevel3 >= 1);
    assertEquals("precondition: same level", r.nodeLevel2, r.nodeLevel3);
    assertEquals("worker B must have won the promotion", 2, r.graph.entryNode());
    // the contract (NeighborArray javadoc): stored score = similarity of owner and neighbor
    assertEquals(
        "NeighborArray scores that are not sim(owner, nbr): " + r.mismatches,
        0,
        r.mismatches.size());
  }
}
