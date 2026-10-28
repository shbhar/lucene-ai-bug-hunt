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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.spatial.prefix.tree.QuadPrefixTree;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.context.SpatialContextFactory;
import org.locationtech.spatial4j.shape.Circle;
import org.locationtech.spatial4j.shape.Rectangle;
import org.locationtech.spatial4j.shape.SpatialRelation;
import org.locationtech.spatial4j.shape.impl.RectangleImpl;

/**
 * HeatmapFacetCounter.calcFacets documents for its inputShape: "We always return a rectangle of
 * integers even if the inputShape isn't a rectangle -- the non-intersecting cells will all be 0."
 *
 * <p>Same setup as TestHeatmapFacetCounter#testQueryCircle (non-geo world, quad tree with 4 levels,
 * a circle input), but one document is a square that the strategy indexes as a single coarse
 * (level-2) leaf cell. Its count is spread over every heatmap cell under that leaf cell, including
 * the cells outside the circle.
 */
public class TestHeatmapAncestorCellsOutsideInputShape extends LuceneTestCase {

  public void testCoarseLeafCountedOutsideCircleInput() throws Exception {
    SpatialContextFactory factory = new SpatialContextFactory();
    factory.geo = false;
    factory.worldBounds = new RectangleImpl(-90, 90, -90, 90, null);
    SpatialContext ctx = factory.newSpatialContext();
    final int level = 4;
    RecursivePrefixTreeStrategy strategy =
        new RecursivePrefixTreeStrategy(new QuadPrefixTree(ctx, level), "geo");

    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        // doc 0: a point inside the circle's bounding box but outside the circle (as upstream)
        Document d0 = new Document();
        for (Field f : strategy.createIndexableFields(ctx.getShapeFactory().pointXY(88, 88))) {
          d0.add(f);
        }
        w.addDocument(d0);
        // doc 1: the square [45,90]x[45,90], exactly one level-2 quad cell (indexed as a leaf)
        Document d1 = new Document();
        for (Field f : strategy.createIndexableFields(ctx.getShapeFactory().rect(45, 90, 45, 90))) {
          d1.add(f);
        }
        w.addDocument(d1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        Circle circle = ctx.getShapeFactory().circle(0, 0, 89);
        HeatmapFacetCounter.Heatmap heatmap =
            strategy.calcFacets(reader.getContext(), null, circle, level, 1000);
        assertEquals(16, heatmap.columns);
        assertEquals(16, heatmap.rows);

        // the top-right heatmap cell [78.75,90]x[78.75,90] does not intersect the circle
        double cellW = heatmap.region.getWidth() / heatmap.columns;
        double cellH = heatmap.region.getHeight() / heatmap.rows;
        int col = heatmap.columns - 1;
        int row = heatmap.rows - 1;
        Rectangle cell =
            ctx.getShapeFactory()
                .rect(
                    heatmap.region.getMinX() + col * cellW,
                    heatmap.region.getMinX() + (col + 1) * cellW,
                    heatmap.region.getMinY() + row * cellH,
                    heatmap.region.getMinY() + (row + 1) * cellH);
        assertEquals(SpatialRelation.DISJOINT, cell.relate(circle));

        int nonZeroOutside = 0;
        StringBuilder detail = new StringBuilder();
        for (int c = 0; c < heatmap.columns; c++) {
          for (int r = 0; r < heatmap.rows; r++) {
            Rectangle hc =
                ctx.getShapeFactory()
                    .rect(
                        heatmap.region.getMinX() + c * cellW,
                        heatmap.region.getMinX() + (c + 1) * cellW,
                        heatmap.region.getMinY() + r * cellH,
                        heatmap.region.getMinY() + (r + 1) * cellH);
            if (hc.relate(circle) == SpatialRelation.DISJOINT && heatmap.getCount(c, r) != 0) {
              nonZeroOutside++;
              detail.append(' ').append(hc).append('=').append(heatmap.getCount(c, r));
            }
          }
        }
        assertEquals(
            "cells that do not intersect the circle must be 0, got:" + detail, 0, nonZeroOutside);
      }
    }
  }
}
