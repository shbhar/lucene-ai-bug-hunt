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
import java.util.Map;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.spatial.prefix.tree.Cell;
import org.apache.lucene.spatial.prefix.tree.CellIterator;
import org.apache.lucene.spatial.prefix.tree.PackedQuadPrefixTree;
import org.apache.lucene.spatial.prefix.tree.QuadPrefixTree;
import org.apache.lucene.spatial.prefix.tree.SpatialPrefixTree;
import org.apache.lucene.spatial.prefix.tree.SpatialPrefixTreeFactory;
import org.apache.lucene.spatial.query.SpatialArgs;
import org.apache.lucene.spatial.query.SpatialOperation;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.context.SpatialContextFactory;
import org.locationtech.spatial4j.shape.Point;
import org.locationtech.spatial4j.shape.Rectangle;
import org.locationtech.spatial4j.shape.Shape;
import org.locationtech.spatial4j.shape.SpatialRelation;
import org.locationtech.spatial4j.shape.impl.RectangleImpl;

/**
 * QuadPrefixTree / PackedQuadPrefixTree with world bounds that are not a dyadic number (here the
 * common Web Mercator square, +/- pi * 6378137): a point lying on a grid line is assigned to a cell
 * whose shape does not contain it, so RecursivePrefixTreeStrategy Intersects queries that contain
 * the point (even the point itself) do not find it. Uses only public API.
 */
public class TestQuadTreeCellEdgeFalseNegative extends LuceneTestCase {

  private static final double HALF = Math.PI * 6378137.0; // 20037508.342789244

  private static SpatialContext nonGeoContext(double minX, double maxX, double minY, double maxY) {
    SpatialContextFactory factory = new SpatialContextFactory();
    factory.geo = false;
    factory.worldBounds = new RectangleImpl(minX, maxX, minY, maxY, null);
    return factory.newSpatialContext();
  }

  /** Indexes {@code indexed} in one document and returns the hit count of Intersects(query). */
  private int intersectsCount(SpatialPrefixTree grid, Shape indexed, Shape query) throws IOException {
    RecursivePrefixTreeStrategy strategy = new RecursivePrefixTreeStrategy(grid, "geo");
    try (Directory dir = newDirectory()) {
      try (IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig())) {
        Document doc = new Document();
        for (Field f : strategy.createIndexableFields(indexed)) {
          doc.add(f);
        }
        writer.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        return searcher.count(
            strategy.makeQuery(new SpatialArgs(SpatialOperation.Intersects, query)));
      }
    }
  }

  /** Longitude 45 degrees in Web Mercator metres (standard spherical formula). */
  private static double x45() {
    return 6378137.0 * Math.toRadians(45);
  }

  public void testPointQueryDoesNotFindTheSamePoint() throws IOException {
    SpatialContext ctx = nonGeoContext(-HALF, HALF, -HALF, HALF);
    SpatialPrefixTree grid = new QuadPrefixTree(ctx); // default 12 levels
    Point p = ctx.getShapeFactory().pointXY(x45(), 0);
    assertEquals("Intersects(p) must match a document indexed at p", 1, intersectsCount(grid, p, p));
  }

  public void testRectangleWhoseEdgeTouchesPointDoesNotFindIt() throws IOException {
    SpatialContext ctx = nonGeoContext(-HALF, HALF, -HALF, HALF);
    SpatialPrefixTree grid = new QuadPrefixTree(ctx);
    Point p = ctx.getShapeFactory().pointXY(x45(), 0);
    Rectangle query = ctx.getShapeFactory().rect(x45(), x45() + 1000, -1000, 1000);
    assertTrue(query.relate(p).intersects()); // the exact relation: the rectangle contains p
    assertEquals(1, intersectsCount(grid, p, query));
  }

  public void testPointOnWorldEastEdge() throws IOException {
    SpatialContext ctx = nonGeoContext(-HALF, HALF, -HALF, HALF);
    Point p = ctx.getShapeFactory().pointXY(HALF, 0); // inside the (closed) world bounds
    assertEquals(1, intersectsCount(new QuadPrefixTree(ctx), p, p));
  }

  public void testPackedQuadPointQueryDoesNotFindTheSamePoint() throws IOException {
    SpatialContext ctx = nonGeoContext(-HALF, HALF, -HALF, HALF);
    Point p = ctx.getShapeFactory().pointXY(x45(), 0);
    assertEquals(1, intersectsCount(new PackedQuadPrefixTree(ctx, 12), p, p));
  }

  /** Root cause, without an index: the cell the tree assigns to p does not contain p. */
  public void testCellAssignedToPointDoesNotContainIt() {
    SpatialContext ctx = nonGeoContext(-HALF, HALF, -HALF, HALF);
    SpatialPrefixTree grid = new QuadPrefixTree(ctx);
    Point p = ctx.getShapeFactory().pointXY(x45(), 0);
    CellIterator cells = grid.getTreeCellIterator(p, grid.getMaxLevels());
    while (cells.hasNext()) {
      Cell cell = cells.next();
      assertNotSame(
          "level " + cell.getLevel() + " cell " + cell + " shape " + cell.getShape() + " excludes " + p,
          SpatialRelation.DISJOINT,
          cell.getShape().relate(p));
    }
  }

  /**
   * The factory default for a non-geo context is a 50-level quad tree; a point query on the top
   * edge of the world makes a cell shape outside the world bounds and throws.
   */
  public void testFactoryDefaultQuadTreeThrowsForPointOnWorldTopEdge() throws IOException {
    SpatialContext ctx = nonGeoContext(-1000, 1000, -1000, 1000);
    SpatialPrefixTree grid =
        SpatialPrefixTreeFactory.makeSPT(
            Map.of("prefixTree", "quad"), getClass().getClassLoader(), ctx);
    assertEquals(50, grid.getMaxLevels());
    Point p = ctx.getShapeFactory().pointXY(0, 1000);
    assertEquals(1, intersectsCount(grid, p, p));
  }
}
