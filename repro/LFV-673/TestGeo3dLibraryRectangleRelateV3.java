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
package org.apache.lucene.spatial.spatial4j;

import org.apache.lucene.spatial.prefix.tree.Cell;
import org.apache.lucene.spatial.prefix.tree.CellIterator;
import org.apache.lucene.spatial.prefix.tree.PackedQuadPrefixTree;
import org.apache.lucene.spatial.prefix.tree.S2PrefixTree;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Shape;
import org.locationtech.spatial4j.shape.SpatialRelation;

/**
 * Non-Geo3d rectangles are produced by Lucene itself in a Geo3d context (no hand-built
 * RectangleImpl): PackedQuadPrefixTree cells and the S2PrefixTree world cell. Relating them with a
 * Geo3d circle gives WITHIN/CONTAINS from the wrong side.
 */
public class TestGeo3dLibraryRectangleRelateV3 extends LuceneTestCase {

  private static final SpatialContext GEO3D = new Geo3dSpatialContextFactory().newSpatialContext();

  /** The level-1 PackedQuad cell [0,180]x[0,90] contains a 1-degree circle at (45,45). */
  public void testPackedQuadCellContainsSmallCircle() {
    Shape circle = GEO3D.getShapeFactory().circle(45, 45, 1);
    PackedQuadPrefixTree grid = new PackedQuadPrefixTree(GEO3D, 4);
    CellIterator it = grid.getWorldCell().getNextLevelCells(null);
    int checked = 0;
    while (it.hasNext()) {
      Cell c = it.next();
      Shape s = c.getShape();
      System.out.println("cell " + s + " class=" + s.getClass().getName() + " rel(circle)=" + s.relate(circle)
          + " circle.rel(cell)=" + circle.relate(s));
      assertFalse("PackedQuad cell shapes are not Geo3dShapes", s instanceof Geo3dShape<?>);
      if (s.getBoundingBox().getMinX() == 0 && s.getBoundingBox().getMinY() == 0) {
        checked++;
        assertEquals("cell contains circle", SpatialRelation.CONTAINS, s.relate(circle));
        assertEquals("circle within cell", SpatialRelation.WITHIN, circle.relate(s));
      }
    }
    assertEquals(1, checked);
  }

  /** The S2PrefixTree world cell's shape is the context's world bounds (a non-Geo3d rectangle). */
  public void testS2WorldCellShapeIsWorldBounds() {
    S2PrefixTree grid = new S2PrefixTree(GEO3D, 3, 1);
    Shape world = grid.getWorldCell().getShape();
    System.out.println("S2 world cell shape class=" + world.getClass().getName());
    assertSame(GEO3D.getWorldBounds(), world);
    Shape circle = GEO3D.getShapeFactory().circle(45, 45, 1);
    assertEquals("world cell contains circle", SpatialRelation.CONTAINS, world.relate(circle));
  }
}
