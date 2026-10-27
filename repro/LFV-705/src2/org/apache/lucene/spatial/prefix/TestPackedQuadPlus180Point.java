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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.spatial.SpatialStrategy;
import org.apache.lucene.spatial.composite.CompositeSpatialStrategy;
import org.apache.lucene.spatial.prefix.tree.PackedQuadPrefixTree;
import org.apache.lucene.spatial.prefix.tree.QuadPrefixTree;
import org.apache.lucene.spatial.prefix.tree.SpatialPrefixTree;
import org.apache.lucene.spatial.query.SpatialArgs;
import org.apache.lucene.spatial.query.SpatialOperation;
import org.apache.lucene.spatial.serialized.SerializedDVStrategy;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Point;
import org.locationtech.spatial4j.shape.Shape;

/**
 * PackedQuadPrefixTree in the geo context: a document indexed at the point (180, 10) is not found
 * by a query with that same point.
 *
 * <p>Indexing walks PackedQuadPrefixTree's PrefixTreeIterator, which keeps the first cell (Z order)
 * whose rectangle relate()s to the point; geo rectangles treat longitude +180 as -180, so the
 * point lands in the cells at the WEST edge. A point query descends with
 * LegacyCell.getNextLevelCells(Point) -> PackedQuadPrefixTree.getCell (mid-point test), which
 * sends x = +180 to the EAST edge. QuadPrefixTree uses getCell on both sides and is consistent.
 */
public class TestPackedQuadPlus180Point extends LuceneTestCase {

  private static final SpatialContext CTX = SpatialContext.GEO;

  private static int count(
      SpatialStrategy indexStrategy, SpatialStrategy queryStrategy, Point indexed, SpatialOperation op, Shape query)
      throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
      Document doc = new Document();
      for (Field f : indexStrategy.createIndexableFields(indexed)) {
        doc.add(f);
      }
      w.addDocument(doc);
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(w)) {
        IndexSearcher searcher = new IndexSearcher(r);
        return searcher.count(queryStrategy.makeQuery(new SpatialArgs(op, query)));
      }
    }
  }

  private static RecursivePrefixTreeStrategy rpt(SpatialPrefixTree grid, boolean pointsOnly) {
    RecursivePrefixTreeStrategy s = new RecursivePrefixTreeStrategy(grid, "geo");
    s.setPointsOnly(pointsOnly);
    return s;
  }

  private static Point pt(double x, double y) {
    return CTX.getShapeFactory().pointXY(x, y);
  }

  /** RPT Intersects with the indexed point itself. */
  public void testRptIntersectsSamePoint() throws IOException {
    Point p = pt(180, 10);
    assertTrue(SpatialOperation.Intersects.evaluate(p, p));
    RecursivePrefixTreeStrategy s = rpt(new PackedQuadPrefixTree(CTX, 8), false);
    assertEquals(
        "RPT/PackedQuadPrefixTree Intersects(Pt(180,10)) must find the doc at Pt(180,10)",
        1,
        count(s, s, p, SpatialOperation.Intersects, p));
  }

  /** RPT Contains (Covers) with the indexed point itself. */
  public void testRptContainsSamePoint() throws IOException {
    Point p = pt(180, 10);
    assertTrue(SpatialOperation.Contains.evaluate(p, p));
    RecursivePrefixTreeStrategy s = rpt(new PackedQuadPrefixTree(CTX, 8), false);
    assertEquals(
        "RPT/PackedQuadPrefixTree Contains(Pt(180,10)) must find the doc at Pt(180,10)",
        1,
        count(s, s, p, SpatialOperation.Contains, p));
  }

  /**
   * CompositeSpatialStrategy is documented as exact; with pointsOnly the RPT index alone finds the
   * document (TermQuery shortcut), the composite does not.
   */
  public void testCompositeIntersectsSamePoint() throws IOException {
    Point p = pt(180, 10);
    CompositeSpatialStrategy c =
        new CompositeSpatialStrategy(
            "geo", rpt(new PackedQuadPrefixTree(CTX, 8), true), new SerializedDVStrategy(CTX, "geo_sdv"));
    assertEquals(
        "CompositeSpatialStrategy Intersects(Pt(180,10)) must find the doc at Pt(180,10)",
        1,
        count(c, c, p, SpatialOperation.Intersects, p));
  }

  /** Control: the same point at -180 is found. */
  public void testControlMinus180() throws IOException {
    Point p = pt(-180, 10);
    RecursivePrefixTreeStrategy s = rpt(new PackedQuadPrefixTree(CTX, 8), false);
    assertEquals(1, count(s, s, p, SpatialOperation.Intersects, p));
  }

  /** Control: QuadPrefixTree (same cell geometry, getCell on both sides) finds the doc at +180. */
  public void testControlQuadPrefixTree() throws IOException {
    Point p = pt(180, 10);
    RecursivePrefixTreeStrategy s = rpt(new QuadPrefixTree(CTX, 8), false);
    assertEquals(1, count(s, s, p, SpatialOperation.Intersects, p));
  }

  /** Control: a rectangle query touching +180 finds the doc (relate-based traversal). */
  public void testControlRectangleQuery() throws IOException {
    Point p = pt(180, 10);
    RecursivePrefixTreeStrategy s = rpt(new PackedQuadPrefixTree(CTX, 8), false);
    assertEquals(
        1,
        count(s, s, p, SpatialOperation.Intersects, CTX.getShapeFactory().rect(179, 180, 9, 11)));
  }
}
