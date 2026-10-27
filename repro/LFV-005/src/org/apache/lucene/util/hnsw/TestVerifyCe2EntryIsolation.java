package org.apache.lucene.util.hnsw;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.lucene.codecs.hnsw.DefaultFlatVectorScorer;
import org.apache.lucene.index.FloatVectorValues;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.KnnCollector;
import org.apache.lucene.search.TaskExecutor;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.FixedBitSet;

/**
 * Independent check of the finding at the util/hnsw level (no codec).
 *
 * <p>Layout: 1450 2-D vectors on a line. X = ord 171 sits at (0,0); three "bridge" ords {0, 4, 215}
 * sit at (1,0), (2,0), (3,0); every other ord i sits at (100+i, 0). With the production seed
 * (HnswGraphBuilder.randSeed = 42) and M=16, ord 171 is the first node of level 2 (the top level),
 * ord 4 is on level 1 and ord 215 on level 2, so X's only links on every level go to the bridge.
 * Deleting the 3 bridge ords (0.2% of the graph) isolates X; X stays the first surviving node of
 * the top level, so copyAndPruneGraphStructure makes it the entry of the reused graph.
 */
public class TestVerifyCe2EntryIsolation extends LuceneTestCase {
  static final int N = 1450, X = 171, M = 16, BEAM = 100;
  static final int[] BRIDGE = {0, 4, 215};
  static final int[] CONTROL_DELETES = {1, 5, 216}; // same count, not X's neighbourhood
  static final VectorSimilarityFunction SIM = VectorSimilarityFunction.EUCLIDEAN;
  static final float[] QUERY = {1000.3f, 0f}; // exact 5-NN: ords 898..902

  static float[] pos(int i) {
    if (i == X) return new float[] {0f, 0f};
    if (i == 0) return new float[] {1f, 0f};
    if (i == 4) return new float[] {2f, 0f};
    if (i == 215) return new float[] {3f, 0f};
    return new float[] {100f + i, 0f};
  }

  static List<float[]> vectors(int[] deleted, int[] newOrdMapOut, List<Integer> newToOld) {
    List<float[]> merged = new ArrayList<>();
    int next = 0;
    for (int i = 0; i < N; i++) {
      final int ii = i;
      if (Arrays.stream(deleted).anyMatch(d -> d == ii)) {
        newOrdMapOut[i] = -1;
      } else {
        newOrdMapOut[i] = next++;
        merged.add(pos(i));
        newToOld.add(i);
      }
    }
    return merged;
  }

  static RandomVectorScorerSupplier supplier(List<float[]> v) throws IOException {
    return DefaultFlatVectorScorer.INSTANCE.getRandomVectorScorerSupplier(
        SIM, FloatVectorValues.fromFloats(v, 2));
  }

  static OnHeapHnswGraph buildSource() throws IOException {
    List<float[]> all = new ArrayList<>();
    for (int i = 0; i < N; i++) all.add(pos(i));
    HnswGraphBuilder b = HnswGraphBuilder.create(supplier(all), M, BEAM, HnswGraphBuilder.randSeed);
    for (int i = 0; i < N; i++) b.addGraphNode(i); // same order as the flush
    return b.getCompletedGraph();
  }

  static int[] nbrs(HnswGraph g, int level, int node) throws IOException {
    g.seek(level, node);
    List<Integer> out = new ArrayList<>();
    for (int n = g.nextNeighbor(); n != Integer.MAX_VALUE; n = g.nextNeighbor()) out.add(n);
    return out.stream().mapToInt(Integer::intValue).toArray();
  }

  static boolean onLevel(HnswGraph g, int level, int node) throws IOException {
    HnswGraph.NodesIterator it = g.getNodesOnLevel(level);
    while (it.hasNext()) if (it.nextInt() == node) return true;
    return false;
  }

  static int reachableOnLevel0(HnswGraph g, int start) throws IOException {
    FixedBitSet seen = new FixedBitSet(g.size());
    ArrayDeque<Integer> q = new ArrayDeque<>();
    q.add(start);
    seen.set(start);
    while (!q.isEmpty()) {
      int n = q.poll();
      for (int m : nbrs(g, 0, n)) {
        if (!seen.getAndSet(m)) q.add(m);
      }
    }
    return seen.cardinality();
  }

  /** Returns the old ords of the approximate top-5 for QUERY. */
  static TreeSet<Integer> knn5(OnHeapHnswGraph g, List<float[]> merged, List<Integer> newToOld)
      throws IOException {
    RandomVectorScorer scorer =
        DefaultFlatVectorScorer.INSTANCE.getRandomVectorScorer(
            SIM, FloatVectorValues.fromFloats(merged, 2), QUERY);
    KnnCollector c = HnswGraphSearcher.search(scorer, 5, g, null, Integer.MAX_VALUE);
    TopDocs td = c.topDocs();
    TreeSet<Integer> res = new TreeSet<>();
    for (var sd : td.scoreDocs) res.add(newToOld.get(sd.doc));
    return res;
  }

  static String describe(String label, OnHeapHnswGraph g, List<float[]> merged, List<Integer> n2o)
      throws IOException {
    int e = g.entryNode();
    StringBuilder sb = new StringBuilder(label);
    sb.append(": entry(new)=").append(e).append(" entry(old)=").append(n2o.get(e));
    sb.append(" numLevels=").append(g.numLevels());
    for (int l = 0; l < g.numLevels(); l++) {
      sb.append(" L").append(l).append("deg=").append(nbrs(g, l, e).length);
    }
    sb.append(" reachableFromEntryL0=").append(reachableOnLevel0(g, e)).append('/').append(g.size());
    sb.append(" knn5(old ords)=").append(knn5(g, merged, n2o));
    return sb.toString();
  }

  /** Preconditions on the source graph, asserted so the scenario is not assumed. */
  public void testPreconditionsOnSourceGraph() throws IOException {
    OnHeapHnswGraph src = buildSource();
    System.out.println("source: numLevels=" + src.numLevels() + " entry=" + src.entryNode());
    assertEquals(3, src.numLevels());
    List<Integer> top = new ArrayList<>();
    HnswGraph.NodesIterator it = src.getNodesOnLevel(2);
    while (it.hasNext()) top.add(it.nextInt());
    System.out.println("source top-level nodes (iteration order) = " + top);
    assertEquals(X, (int) top.get(0)); // first node of the top level
    for (int l = 0; l < 3; l++) {
      int[] xn = nbrs(src, l, X);
      System.out.println("source X level " + l + " neighbours " + Arrays.toString(xn));
      for (int n : xn) assertTrue("X links outside bridge: " + n, n == 0 || n == 4 || n == 215);
    }
    for (int l = 0; l < 3; l++) {
      HnswGraph.NodesIterator nodes = src.getNodesOnLevel(l);
      while (nodes.hasNext()) {
        int n = nodes.nextInt();
        if (n == 0 || n == 4 || n == 215 || n == X) continue;
        for (int m : nbrs(src, l, n)) assertTrue("in-link to X from " + n, m != X);
      }
    }
    // the source graph answers the query correctly
    List<Integer> ident = new ArrayList<>();
    List<float[]> all = new ArrayList<>();
    for (int i = 0; i < N; i++) {
      ident.add(i);
      all.add(pos(i));
    }
    TreeSet<Integer> r = knn5(src, all, ident);
    System.out.println("source knn5 = " + r);
    assertEquals(new TreeSet<>(List.of(898, 899, 900, 901, 902)), r);
  }

  private void runSerial(int[] deleted, boolean expectBug) throws IOException {
    OnHeapHnswGraph src = buildSource();
    int[] map = new int[N];
    List<Integer> n2o = new ArrayList<>();
    List<float[]> merged = vectors(deleted, map, n2o);
    OnHeapHnswGraph g =
        InitializedHnswGraphBuilder.initGraph(src, map, merged.size(), BEAM, supplier(merged));
    String d = describe("serial initGraph deletes=" + Arrays.toString(deleted), g, merged, n2o);
    System.out.println(d);
    check(g, merged, n2o, d, expectBug);
  }

  private void runConcurrent(int[] deleted, boolean expectBug) throws Exception {
    OnHeapHnswGraph src = buildSource();
    int[] map = new int[N];
    List<Integer> n2o = new ArrayList<>();
    List<float[]> merged = vectors(deleted, map, n2o);
    RandomVectorScorerSupplier sup = supplier(merged);
    InitializedHnswGraphBuilder.PrunedGraph pg =
        InitializedHnswGraphBuilder.pruneGraph(sup, BEAM, src, map, merged.size(), null);
    assertTrue(pg.hasDeletes());
    FixedBitSet init = new FixedBitSet(merged.size());
    init.set(0, merged.size());
    ExecutorService exec = Executors.newFixedThreadPool(4);
    try {
      HnswConcurrentMergeBuilder b =
          new HnswConcurrentMergeBuilder(
              new TaskExecutor(exec), 4, sup, BEAM, pg.graph(), init, pg);
      OnHeapHnswGraph g = b.build(merged.size());
      String d = describe("concurrent(4) deletes=" + Arrays.toString(deleted), g, merged, n2o);
      System.out.println(d);
      check(g, merged, n2o, d, expectBug);
    } finally {
      exec.shutdown();
      exec.awaitTermination(1, TimeUnit.MINUTES);
    }
  }

  private void check(
      OnHeapHnswGraph g, List<float[]> merged, List<Integer> n2o, String d, boolean expectBug)
      throws IOException {
    int e = g.entryNode();
    if (expectBug) {
      assertEquals("entry is X", X, (int) n2o.get(e));
    }
    // Expected behaviour of a correct repair: the entry has a neighbour on level 0 and the
    // merged graph still answers the query like the source graph did.
    assertTrue("entry has no level-0 neighbour: " + d, nbrs(g, 0, e).length > 0);
    assertEquals(d, new TreeSet<>(List.of(898, 899, 900, 901, 902)), knn5(g, merged, n2o));
  }

  public void testSerialBridgeDeleted() throws IOException {
    runSerial(BRIDGE, true);
  }

  public void testConcurrentBridgeDeleted() throws Exception {
    runConcurrent(BRIDGE, true);
  }

  public void testSerialControlDeletes() throws IOException {
    runSerial(CONTROL_DELETES, false);
  }

  public void testConcurrentControlDeletes() throws Exception {
    runConcurrent(CONTROL_DELETES, false);
  }
}
