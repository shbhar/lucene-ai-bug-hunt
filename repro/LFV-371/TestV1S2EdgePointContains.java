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
package org.apache.lucene.spatial.prefix;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.spatial.prefix.tree.Cell;
import org.apache.lucene.spatial.prefix.tree.CellIterator;
import org.apache.lucene.spatial.prefix.tree.GeohashPrefixTree;
import org.apache.lucene.spatial.prefix.tree.QuadPrefixTree;
import org.apache.lucene.spatial.prefix.tree.S2PrefixTree;
import org.apache.lucene.spatial.prefix.tree.SpatialPrefixTree;
import org.apache.lucene.spatial.query.SpatialArgs;
import org.apache.lucene.spatial.query.SpatialOperation;
import org.apache.lucene.spatial.spatial4j.Geo3dSpatialContextFactory;
import org.apache.lucene.spatial3d.geom.PlanetModel;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Point;

/**
 * Independent check: with RecursivePrefixTreeStrategy over S2PrefixTree, a document whose indexed
 * shape is a point P must match SpatialOperation.Contains with query shape P (Contains is "Covers",
 * boundary-neutral, and SpatialOperation.Contains.evaluate(P, P) is true). Only public API is used.
 */
public class TestV1S2EdgePointContains extends LuceneTestCase {

  private static SpatialContext geo3dContext() {
    return new Geo3dSpatialContextFactory().newSpatialContext();
  }

  private static SpatialContext geo3dContext(PlanetModel planetModel) {
    Geo3dSpatialContextFactory factory = new Geo3dSpatialContextFactory();
    factory.planetModel = planetModel;
    return factory.newSpatialContext();
  }

  /** Indexes one document with {@code indexed}; returns the hit count of {@code op(query)}. */
  private int hits(
      SpatialPrefixTree grid,
      boolean pruneLeafyBranches,
      Point indexed,
      SpatialOperation op,
      Point query)
      throws IOException {
    RecursivePrefixTreeStrategy strategy = new RecursivePrefixTreeStrategy(grid, "geo");
    strategy.setPruneLeafyBranches(pruneLeafyBranches);
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        for (Field f : strategy.createIndexableFields(indexed)) {
          doc.add(f);
        }
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        return s.count(strategy.makeQuery(new SpatialArgs(op, query)));
      }
    }
  }

  /** Max number of next-level cells the query-side traversal accepts for the point, at any level. */
  private static int maxTouchingChildren(SpatialPrefixTree grid, Point p) {
    // Walk down from the world cell, following the indexing-side cell of each level, and count how
    // many children each level's getNextLevelCells(p) returns.
    int max = 0;
    Cell cell = grid.getWorldCell();
    List<Cell> indexingPath = new ArrayList<>();
    CellIterator it = grid.getTreeCellIterator(p, grid.getMaxLevels());
    while (it.hasNext()) {
      indexingPath.add(it.next());
    }
    for (Cell target : indexingPath) {
      if (cell.getLevel() >= grid.getMaxLevels()) {
        break;
      }
      CellIterator kids = cell.getNextLevelCells(p);
      int n = 0;
      Cell next = null;
      while (kids.hasNext()) {
        Cell k = kids.next();
        n++;
        if (k.getTokenBytesNoLeaf(null).equals(target.getTokenBytesNoLeaf(null))) {
          next = k;
        }
      }
      max = Math.max(max, n);
      if (next == null) {
        break;
      }
      cell = next;
    }
    return max;
  }

  /** The main claim: S2 + an edge point + Contains(same point) returns no hit. */
  public void testS2EdgePointContainsItself() throws IOException {
    // (lon, lat): two points on the equator (an S2 child-cell edge on faces 0 and 3), one on
    // the lon=45 meridian (the face 0 / face 1 edge) and (0, 0), the centre of face 0 where four
    // child cells meet.
    double[][] lonLat = {{10, 0}, {160, 0}, {45, 20}, {0, 0}};
    List<String> misses = new ArrayList<>();
    int configs = 0;
    for (PlanetModel pm : new PlanetModel[] {PlanetModel.SPHERE, PlanetModel.WGS84})
    for (double[] ll : lonLat) {
      SpatialContext ctx = geo3dContext(pm);
      Point p = ctx.getShapeFactory().pointXY(ll[0], ll[1]);
      assertTrue("Contains.evaluate(p, p)", SpatialOperation.Contains.evaluate(p, p));
      for (int arity = 1; arity <= 3; arity++) {
        for (int maxLevels : new int[] {3, S2PrefixTree.getMaxLevels(arity)}) {
          for (boolean prune : new boolean[] {true, false}) {
            S2PrefixTree grid = new S2PrefixTree(ctx, maxLevels, arity);
            configs++;
            int intersects = hits(grid, prune, p, SpatialOperation.Intersects, p);
            assertEquals("control: Intersects finds the doc for " + p, 1, intersects);
            int contains = hits(grid, prune, p, SpatialOperation.Contains, p);
            if (contains != 1) {
              misses.add(
                  pm + " point="
                      + p
                      + " arity="
                      + arity
                      + " maxLevels="
                      + maxLevels
                      + " pruneLeafy="
                      + prune
                      + " intersects="
                      + intersects
                      + " contains="
                      + contains
                      + " maxTouchingChildren="
                      + maxTouchingChildren(grid, p));
            }
          }
        }
      }
    }
    if (!misses.isEmpty()) {
      fail(
          misses.size()
              + " of "
              + configs
              + " S2 configs: Contains(p) misses the doc indexed with p:\n  "
              + String.join("\n  ", misses));
    }
  }

  /** Control: an S2 point that is not on a cell edge is found by Contains(same point). */
  public void testS2OffEdgePointContainsItself() throws IOException {
    SpatialContext ctx = geo3dContext();
    Point p = ctx.getShapeFactory().pointXY(10.3, 0.7);
    for (int arity = 1; arity <= 3; arity++) {
      for (int maxLevels : new int[] {3, 8}) {
        S2PrefixTree grid = new S2PrefixTree(ctx, maxLevels, arity);
        assertEquals("maxTouchingChildren", 1, maxTouchingChildren(grid, p));
        assertEquals(1, hits(grid, true, p, SpatialOperation.Contains, p));
      }
    }
  }

  /** Control: geohash and quad trees (same Geo3d context, same edge points) find the doc. */
  public void testLegacyTreesEdgePointContainsItself() throws IOException {
    SpatialContext ctx = geo3dContext();
    double[][] lonLat = {{10, 0}, {160, 0}, {45, 20}, {0, 0}};
    for (double[] ll : lonLat) {
      Point p = ctx.getShapeFactory().pointXY(ll[0], ll[1]);
      for (SpatialPrefixTree grid :
          new SpatialPrefixTree[] {new GeohashPrefixTree(ctx, 4), new QuadPrefixTree(ctx, 8)}) {
        assertEquals(
            grid + " " + p, 1, hits(grid, true, p, SpatialOperation.Contains, p));
      }
    }
  }
}
