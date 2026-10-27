package org.apache.lucene.util.hnsw;

import static org.apache.lucene.search.DocIdSetIterator.NO_MORE_DOCS;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.lucene.codecs.hnsw.DefaultFlatVectorScorer;
import org.apache.lucene.index.FloatVectorValues;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.KnnCollector;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.FixedBitSet;

/**
 * Independent verification of "addConnections descends to the WORST upper-level candidate".
 *
 * <p>Test 1 observes the entry point that HnswGraphBuilder.addConnections hands to each
 * searchLevel call, on a small fully connected graph, by injecting a recording HnswGraphSearcher
 * through the protected HnswGraphBuilder constructor. No production code is modified.
 *
 * <p>Test 2 shows the consequence through the public InitializedHnswGraphBuilder.initGraph API (the
 * merge-reuse path): a node whose only neighbor was deleted is re-linked from scratch by
 * addConnections, and the anti-greedy descent lands it in the wrong region of a 3-level graph.
 */
public class TestAddConnectionsEntryChoice extends LuceneTestCase {

  /** Records (level, entry points) of every searchLevel call. */
  static final class RecordingSearcher extends HnswGraphSearcher {
    final List<String> calls = new ArrayList<>();
    final List<int[]> eps = new ArrayList<>();
    final List<Integer> levels = new ArrayList<>();

    RecordingSearcher(int size) {
      super(new NeighborQueue(16, true), new FixedBitSet(size));
    }

    @Override
    void searchLevel(
        KnnCollector results,
        RandomVectorScorer scorer,
        int level,
        int[] entryPoints,
        HnswGraph graph,
        Bits acceptOrds)
        throws IOException {
      levels.add(level);
      eps.add(entryPoints.clone());
      calls.add("level=" + level + " eps=" + Arrays.toString(entryPoints));
      super.searchLevel(results, scorer, level, entryPoints, graph, acceptOrds);
    }
  }

  static final class InstrumentedBuilder extends HnswGraphBuilder {
    InstrumentedBuilder(
        RandomVectorScorerSupplier s, int beamWidth, OnHeapHnswGraph g, HnswGraphSearcher gs)
        throws IOException {
      super(s, beamWidth, 1L, g, null, gs);
    }
  }

  static RandomVectorScorerSupplier supplier1d(float[] pos) throws IOException {
    List<float[]> l = new ArrayList<>();
    for (float p : pos) {
      l.add(new float[] {p});
    }
    return DefaultFlatVectorScorer.INSTANCE.getRandomVectorScorerSupplier(
        VectorSimilarityFunction.EUCLIDEAN, FloatVectorValues.fromFloats(l, 1));
  }

  static void undirected(OnHeapHnswGraph g, int level, int a, int b) {
    g.getNeighbors(level, a).addOutOfOrder(b, Float.NaN);
    g.getNeighbors(level, b).addOutOfOrder(a, Float.NaN);
  }

  static List<Integer> neighbors(HnswGraph g, int level, int node) throws IOException {
    List<Integer> out = new ArrayList<>();
    g.seek(level, node);
    for (int n = g.nextNeighbor(); n != NO_MORE_DOCS; n = g.nextNeighbor()) {
      out.add(n);
    }
    return out;
  }

  /**
   * Level 1 = chain 4-3-2-1-0 (positions 4,3,2,1,0), entry node = 4. Level 0 = same chain plus the
   * unlinked node X=5 at position -0.5. A greedy descent toward X must hand ordinal 0 (position 0,
   * the closest level-1 node) to the level-0 search.
   */
  public void testLevel0SearchStartsFromClosestUpperLevelNode() throws IOException {
    float[] pos = {0f, 1f, 2f, 3f, 4f, -0.5f};
    int x = 5;
    OnHeapHnswGraph g = new OnHeapHnswGraph(16, pos.length);
    for (int n = 0; n <= 4; n++) {
      g.addNode(1, n);
      g.addNode(0, n);
    }
    g.addNode(0, x);
    g.trySetNewEntryNode(4, 1);
    for (int n = 0; n < 4; n++) {
      undirected(g, 1, n, n + 1);
      undirected(g, 0, n, n + 1);
    }
    RecordingSearcher rs = new RecordingSearcher(pos.length);
    InstrumentedBuilder b = new InstrumentedBuilder(supplier1d(pos), 100, g, rs);
    b.scorer.setScoringOrdinal(x); // as fixDisconnectedNodes / rebalanceGraph do
    b.addConnections(x, 0, b.scorer);

    System.out.println("searchLevel calls: " + rs.calls);
    System.out.println("X level-0 neighbors after addConnections: " + neighbors(g, 0, x));
    assertEquals(2, rs.levels.size());
    assertEquals(1, (int) rs.levels.get(0));
    assertEquals(0, (int) rs.levels.get(1));
    assertArrayEquals(new int[] {4}, rs.eps.get(0)); // descent starts at the entry node
    assertArrayEquals(
        "level-0 search should start from the level-1 node closest to X (ordinal 0, pos 0); "
            + "got "
            + Arrays.toString(rs.eps.get(1))
            + " (ordinal 4 = pos 4 = the FARTHEST level-1 node)",
        new int[] {0},
        rs.eps.get(1));
  }

  /**
   * Public merge-reuse API. Three levels, 1-D Euclidean.
   *
   * <pre>
   * ord name pos   levels   edges
   *  0  E     0    0..2     L2: E-P   L1: E-A E-B E-P   L0: E-A E-B A-N
   *  1  P   100    0..2     L1: P-Q                     L0: P-Q Q-R P-R
   *  2  A     9    0..1
   *  3  B    -9    0..1
   *  4  Q   101    0..1
   *  5  N    11    0
   *  6  R   102    0
   *  7  X    10    0                                    L0: X-D
   *  8  D  10.5    0        (deleted)
   * </pre>
   *
   * After deleting D, X has no neighbor and is re-linked by addConnections(X, 0). Greedy descent:
   * L2 best=E, L1 from E best=A, L0 from A finds A and N (distance 1 each). Observed: L2 pops P (the
   * worst), L1 from P pops Q (the worst), and the L0 search from Q only reaches {P, Q, R}.
   */
  public void testRepairedNodeLinkedToItsRegionViaPublicInitGraph() throws IOException {
    float[] pos = {0f, 100f, 9f, -9f, 101f, 11f, 102f, 10f, 10.5f};
    OnHeapHnswGraph src = new OnHeapHnswGraph(16, pos.length);
    for (int n : new int[] {0, 1}) {
      src.addNode(2, n);
    }
    for (int n : new int[] {0, 1, 2, 3, 4}) {
      src.addNode(1, n);
    }
    for (int n = 0; n < pos.length; n++) {
      src.addNode(0, n);
    }
    src.trySetNewEntryNode(0, 2);
    undirected(src, 2, 0, 1);
    undirected(src, 1, 0, 2);
    undirected(src, 1, 0, 3);
    undirected(src, 1, 0, 1);
    undirected(src, 1, 1, 4);
    undirected(src, 0, 0, 2);
    undirected(src, 0, 0, 3);
    undirected(src, 0, 2, 5);
    undirected(src, 0, 1, 4);
    undirected(src, 0, 4, 6);
    undirected(src, 0, 1, 6);
    undirected(src, 0, 7, 8);

    int[] newOrdMap = {0, 1, 2, 3, 4, 5, 6, 7, -1};
    float[] live = Arrays.copyOf(pos, 8);
    OnHeapHnswGraph merged =
        InitializedHnswGraphBuilder.initGraph(src, newOrdMap, live.length, 100, supplier1d(live));
    List<Integer> xn = neighbors(merged, 0, 7);
    System.out.println("merged graph: X (ord 7, pos 10) level-0 neighbors = " + xn);
    for (int n : xn) {
      System.out.println("  neighbor " + n + " at pos " + live[n] + " (|d|=" + Math.abs(live[n] - 10f) + ")");
    }
    assertTrue(
        "X should be linked to N=5 or A=2 (distance 1), got " + xn,
        xn.contains(5) || xn.contains(2));
  }
}
