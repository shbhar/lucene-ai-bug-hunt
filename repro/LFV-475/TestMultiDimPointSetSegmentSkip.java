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
package org.example.verify;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PointInSetQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * Independent check: a multi-dimensional {@link PointInSetQuery} must match every document whose
 * point is in the set. Uses only public/protected API (the protected constructor through an
 * anonymous subclass, which is the documented extension point), from a package outside Lucene.
 */
public class TestMultiDimPointSetSegmentSkip extends LuceneTestCase {

  private static final String F = "p";

  /** A multi-dim int set query; packed points are fed in sorted (unsigned byte) order. */
  static Query intSet(int numDims, int[]... points) {
    byte[][] packed = new byte[points.length][];
    for (int i = 0; i < points.length; i++) {
      assertEquals(numDims, points[i].length);
      packed[i] = new byte[numDims * Integer.BYTES];
      for (int d = 0; d < numDims; d++) {
        IntPoint.encodeDimension(points[i][d], packed[i], d * Integer.BYTES);
      }
    }
    Arrays.sort(packed, (a, b) -> Arrays.compareUnsigned(a, b));
    return new PointInSetQuery(
        F,
        numDims,
        Integer.BYTES,
        new PointInSetQuery.Stream() {
          int upto;

          @Override
          public BytesRef next() {
            return upto == packed.length ? null : new BytesRef(packed[upto++]);
          }
        }) {
      @Override
      protected String toString(byte[] value) {
        StringBuilder sb = new StringBuilder("(");
        for (int d = 0; d < numDims; d++) {
          if (d > 0) {
            sb.append(',');
          }
          sb.append(IntPoint.decodeDimension(value, d * Integer.BYTES));
        }
        return sb.append(')').toString();
      }
    };
  }

  /** Oracle independent of PointInSetQuery: OR of exact-match boxes. */
  static Query orOfExactBoxes(int[]... points) {
    BooleanQuery.Builder b = new BooleanQuery.Builder();
    for (int[] p : points) {
      b.add(IntPoint.newRangeQuery(F, p, p), BooleanClause.Occur.SHOULD);
    }
    return b.build();
  }

  /** One segment per entry of {@code segments}; each entry is the list of points (one per doc). */
  static Directory index(int[][]... segments) throws IOException {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig(); // default codec, production-like
    iwc.setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int[][] seg : segments) {
        for (int[] p : seg) {
          Document doc = new Document();
          doc.add(new IntPoint(F, p));
          w.addDocument(doc);
        }
        w.commit();
      }
    }
    return dir;
  }

  static IndexSearcher searcher(DirectoryReader r) {
    IndexSearcher s = new IndexSearcher(r);
    s.setQueryCache(null);
    return s;
  }

  /**
   * Set {(3,50),(7,2)}: the sorted first point is (3,50). The only document holds (7,2), which is
   * in the set. Dimension 1 of the first point (50) is above the segment max in dimension 1 (2).
   */
  public void testFirstSortedPointAboveSegmentMaxInDim1() throws IOException {
    try (Directory dir = index(new int[][] {{7, 2}});
        DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = searcher(r);
      // Controls: the point alone matches, and the OR-of-exact-boxes oracle matches.
      assertEquals("single-point set {(7,2)}", 1, s.count(intSet(2, new int[] {7, 2})));
      assertEquals("oracle", 1, s.count(orOfExactBoxes(new int[] {3, 50}, new int[] {7, 2})));
      Query q = intSet(2, new int[] {3, 50}, new int[] {7, 2});
      TopDocs td = s.search(q, 10);
      assertEquals(q + " search() hits", 1, td.scoreDocs.length);
      assertEquals(q + " count()", 1, s.count(q));
    }
  }

  /**
   * Same set {(3,50),(7,2)}: the sorted last point is (7,2). The only document holds (3,50), which
   * is in the set. Dimension 1 of the last point (2) is below the segment min in dimension 1 (50).
   */
  public void testLastSortedPointBelowSegmentMinInDim1() throws IOException {
    try (Directory dir = index(new int[][] {{3, 50}});
        DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = searcher(r);
      assertEquals("single-point set {(3,50)}", 1, s.count(intSet(2, new int[] {3, 50})));
      assertEquals("oracle", 1, s.count(orOfExactBoxes(new int[] {3, 50}, new int[] {7, 2})));
      Query q = intSet(2, new int[] {3, 50}, new int[] {7, 2});
      TopDocs td = s.search(q, 10);
      assertEquals(q + " search() hits", 1, td.scoreDocs.length);
      assertEquals(q + " count()", 1, s.count(q));
    }
  }

  /** Two one-doc segments: both segments are skipped, so the query matches nothing. */
  public void testTwoSegmentsBothSkipped() throws IOException {
    try (Directory dir = index(new int[][] {{7, 2}}, new int[][] {{3, 50}});
        DirectoryReader r = DirectoryReader.open(dir)) {
      assertEquals(2, r.leaves().size());
      IndexSearcher s = searcher(r);
      int[][] set = {{3, 50}, {7, 2}};
      assertEquals("oracle", 2, s.count(orOfExactBoxes(set)));
      StringBuilder perLeaf = new StringBuilder();
      Query q = intSet(2, set);
      for (LeafReaderContext ctx : r.leaves()) {
        IndexSearcher ls = new IndexSearcher(ctx.reader());
        ls.setQueryCache(null);
        perLeaf
            .append(" leaf")
            .append(ctx.ord)
            .append(": oracle=")
            .append(ls.count(orOfExactBoxes(set)))
            .append(" query=")
            .append(ls.count(q));
      }
      assertEquals(q + " count();" + perLeaf, 2, s.count(q));
    }
  }

  /**
   * Deterministic random differential test (seeded by the test seed) against a brute-force
   * reference over the indexed values, for numDims 1 (control), 2 and 3. Many small segments, so
   * segment bounding boxes are tight.
   */
  public void testRandomDifferential1D() throws IOException {
    differential(1);
  }

  public void testRandomDifferential2D() throws IOException {
    differential(2);
  }

  public void testRandomDifferential3D() throws IOException {
    differential(3);
  }

  private void differential(int numDims) throws IOException {
    Random rnd = new Random(random().nextLong());
    int numSegs = 40;
    List<int[][]> segs = new ArrayList<>();
    List<int[]> all = new ArrayList<>();
    for (int i = 0; i < numSegs; i++) {
      int n = 1 + rnd.nextInt(4);
      int[][] seg = new int[n][];
      for (int j = 0; j < n; j++) {
        seg[j] = new int[numDims];
        for (int d = 0; d < numDims; d++) {
          seg[j][d] = rnd.nextInt(6) - 2; // includes negatives
        }
        all.add(seg[j]);
      }
      segs.add(seg);
    }
    int mismatches = 0;
    String first = null;
    int queries = 300;
    try (Directory dir = index(segs.toArray(new int[0][][]));
        DirectoryReader r = DirectoryReader.open(dir)) {
      assertEquals(numSegs, r.leaves().size());
      IndexSearcher s = searcher(r);
      for (int qi = 0; qi < queries; qi++) {
        int k = 1 + rnd.nextInt(5);
        int[][] set = new int[k][];
        Set<List<Integer>> setKeys = new HashSet<>();
        for (int i = 0; i < k; i++) {
          // half of the time draw a set point from the indexed points, so there are hits
          set[i] =
              rnd.nextBoolean()
                  ? all.get(rnd.nextInt(all.size())).clone()
                  : randomPoint(rnd, numDims);
          setKeys.add(key(set[i]));
        }
        int expected = 0;
        for (int[] p : all) {
          if (setKeys.contains(key(p))) {
            expected++;
          }
        }
        int oracle = s.count(orOfExactBoxes(set));
        assertEquals("brute force vs OR-of-boxes oracle disagree", expected, oracle);
        Query q = intSet(numDims, set);
        int actual = s.count(q);
        if (actual != expected) {
          mismatches++;
          if (first == null) {
            first = q + " expected=" + expected + " actual=" + actual;
          }
        }
      }
    }
    assertEquals(
        numDims + "-D: " + mismatches + "/" + queries + " set queries wrong; first: " + first,
        0,
        mismatches);
  }

  private static int[] randomPoint(Random rnd, int numDims) {
    int[] p = new int[numDims];
    for (int d = 0; d < numDims; d++) {
      p[d] = rnd.nextInt(6) - 2;
    }
    return p;
  }

  private static List<Integer> key(int[] p) {
    List<Integer> l = new ArrayList<>();
    for (int v : p) {
      l.add(v);
    }
    return l;
  }
}
