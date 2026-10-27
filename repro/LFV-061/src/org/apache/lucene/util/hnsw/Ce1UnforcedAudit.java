package org.apache.lucene.util.hnsw;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.lucene.search.TaskExecutor;

/**
 * Unforced audit: run the production HnswConcurrentMergeBuilder.build() (default batch size 2048,
 * shared HnswGraphBuilder.randSeed, real thread pool) on random vectors, and count NeighborArray
 * entries whose stored score is neither NaN nor sim(owner, neighbor). No hooks, no forced
 * interleaving.
 *
 * <p>usage: Ce1UnforcedAudit builds n dim workers M beamWidth
 */
public class Ce1UnforcedAudit {

  static final class Scorer implements UpdateableRandomVectorScorer {
    final float[][] v;
    int ord = -1;

    Scorer(float[][] v) {
      this.v = v;
    }

    @Override
    public void setScoringOrdinal(int node) {
      ord = node;
    }

    @Override
    public float score(int node) {
      return sim(v, ord, node);
    }

    @Override
    public int maxOrd() {
      return v.length;
    }
  }

  static final class Supplier implements RandomVectorScorerSupplier {
    final float[][] v;

    Supplier(float[][] v) {
      this.v = v;
    }

    @Override
    public UpdateableRandomVectorScorer scorer() {
      return new Scorer(v);
    }

    @Override
    public RandomVectorScorerSupplier copy() {
      return new Supplier(v);
    }
  }

  static float sim(float[][] v, int a, int b) {
    float[] x = v[a], y = v[b];
    float d = 0;
    for (int i = 0; i < x.length; i++) {
      float t = x[i] - y[i];
      d += t * t;
    }
    return 1f / (1f + d);
  }

  public static void main(String[] args) throws Exception {
    int builds = Integer.parseInt(args[0]);
    int n = Integer.parseInt(args[1]);
    int dim = Integer.parseInt(args[2]);
    int workers = Integer.parseInt(args[3]);
    int m = Integer.parseInt(args[4]);
    int beam = Integer.parseInt(args[5]);
    ExecutorService es = Executors.newFixedThreadPool(workers);
    int buildsWithBad = 0;
    long totalBad = 0, totalEntries = 0, badUpper = 0;
    long t0 = System.nanoTime();
    try {
      for (int b = 0; b < builds; b++) {
        Random r = new Random(1000 + b);
        float[][] v = new float[n][dim];
        for (float[] row : v) for (int i = 0; i < dim; i++) row[i] = r.nextFloat();
        OnHeapHnswGraph g = new OnHeapHnswGraph(m, n);
        HnswConcurrentMergeBuilder builder =
            new HnswConcurrentMergeBuilder(
                new TaskExecutor(es), workers, new Supplier(v), beam, g, null);
        builder.build(n);
        int bad = 0;
        StringBuilder where = new StringBuilder();
        for (int level = 0; level < g.numLevels(); level++) {
          for (int node = 0; node < n; node++) {
            if (g.nodeExistAtLevel(level, node) == false) continue;
            NeighborArray na = g.getNeighbors(level, node);
            for (int i = 0; i < na.size(); i++) {
              totalEntries++;
              float s = na.getScores(i);
              if (Float.isNaN(s) == false && s != sim(v, node, na.nodes()[i])) {
                bad++;
                if (level > 0) badUpper++;
                if (where.length() < 200) {
                  where.append(" L").append(level).append(':').append(node).append("->");
                  where.append(na.nodes()[i]);
                }
              }
            }
          }
        }
        if (bad > 0) {
          buildsWithBad++;
          System.out.println(
              "build " + b + ": " + bad + " wrong-score entries, numLevels=" + g.numLevels()
                  + where);
        }
        totalBad += bad;
      }
    } finally {
      es.shutdown();
    }
    System.out.printf(
        "SUMMARY builds=%d n=%d dim=%d workers=%d M=%d beam=%d buildsWithWrongScores=%d"
            + " wrongEntries=%d (upperLevel=%d) of %d entries, %.1f s%n",
        builds, n, dim, workers, m, beam, buildsWithBad, totalBad, badUpper, totalEntries,
        (System.nanoTime() - t0) / 1e9);
  }
}
