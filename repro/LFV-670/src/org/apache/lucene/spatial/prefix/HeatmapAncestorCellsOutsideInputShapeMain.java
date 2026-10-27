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

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.spatial.prefix.tree.QuadPrefixTree;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.context.SpatialContextFactory;
import org.locationtech.spatial4j.shape.Circle;
import org.locationtech.spatial4j.shape.Rectangle;
import org.locationtech.spatial4j.shape.SpatialRelation;
import org.locationtech.spatial4j.shape.impl.RectangleImpl;

/** main() variant of TestHeatmapAncestorCellsOutsideInputShape for 7.x/8.x jars (RAMDirectory). */
public class HeatmapAncestorCellsOutsideInputShapeMain {
  public static void main(String[] args) throws Exception {
    SpatialContextFactory factory = new SpatialContextFactory();
    factory.geo = false;
    factory.worldBounds = new RectangleImpl(-90, 90, -90, 90, null);
    SpatialContext ctx = factory.newSpatialContext();
    final int level = 4;
    RecursivePrefixTreeStrategy strategy =
        new RecursivePrefixTreeStrategy(new QuadPrefixTree(ctx, level), "geo");
    Directory dir = new RAMDirectory();
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()));
    Document d0 = new Document();
    for (Field f : strategy.createIndexableFields(ctx.getShapeFactory().pointXY(88, 88))) d0.add(f);
    w.addDocument(d0);
    Document d1 = new Document();
    for (Field f : strategy.createIndexableFields(ctx.getShapeFactory().rect(45, 90, 45, 90))) d1.add(f);
    w.addDocument(d1);
    w.close();
    DirectoryReader reader = DirectoryReader.open(dir);
    Circle circle = ctx.getShapeFactory().circle(0, 0, 89);
    HeatmapFacetCounter.Heatmap heatmap =
        strategy.calcFacets(reader.getContext(), null, circle, level, 1000);
    double cellW = heatmap.region.getWidth() / heatmap.columns;
    double cellH = heatmap.region.getHeight() / heatmap.rows;
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
    reader.close();
    dir.close();
    System.out.println(heatmap + " non-zero cells outside the circle: " + nonZeroOutside + detail);
    if (nonZeroOutside != 0) {
      throw new AssertionError("cells that do not intersect the circle must be 0, got " + nonZeroOutside);
    }
  }
}
