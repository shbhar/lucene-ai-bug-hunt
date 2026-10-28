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
package heatmapprobe;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.spatial.prefix.HeatmapFacetCounter;
import org.apache.lucene.spatial.prefix.RecursivePrefixTreeStrategy;
import org.apache.lucene.spatial.prefix.tree.QuadPrefixTree;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.context.SpatialContextFactory;
import org.locationtech.spatial4j.shape.Rectangle;
import org.locationtech.spatial4j.shape.Shape;
import org.locationtech.spatial4j.shape.impl.RectangleImpl;

/**
 * Prints the heatmap of the repro (non-geo world [-90,90]^2, QuadPrefixTree with 4 levels, the
 * point (88,88) and the square [45,90]^2, circle(0,0,89) input, facetLevel 4) as a grid of the
 * top-right 6 x 6 cells, each cell marked with its relation to the circle, plus totals.
 */
public class HeatmapGridProbe {
  public static void main(String[] args) throws Exception {
    String label = args.length > 0 ? args[0] : "run";
    SpatialContextFactory factory = new SpatialContextFactory();
    factory.geo = false;
    factory.worldBounds = new RectangleImpl(-90, 90, -90, 90, null);
    SpatialContext ctx = factory.newSpatialContext();
    RecursivePrefixTreeStrategy strategy =
        new RecursivePrefixTreeStrategy(new QuadPrefixTree(ctx, 4), "geo");
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Shape[] docs = {ctx.getShapeFactory().pointXY(88, 88), ctx.getShapeFactory().rect(45, 90, 45, 90)};
        for (Shape s : docs) {
          Document d = new Document();
          for (Field f : strategy.createIndexableFields(s)) {
            d.add(f);
          }
          w.addDocument(d);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        Shape circle = ctx.getShapeFactory().circle(0, 0, 89);
        HeatmapFacetCounter.Heatmap hm = strategy.calcFacets(reader.getContext(), null, circle, 4, 1000);
        double cw = hm.region.getWidth() / hm.columns;
        double ch = hm.region.getHeight() / hm.rows;
        System.out.println("PROBE " + label + " heatmap " + hm);
        System.out.println("PROBE " + label + " top-right 6x6 cells, x from 22.5 to 90 (columns 10-15), y from 90 down to 22.5; count + (D = cell disjoint from the circle, i = intersects)");
        int nonZeroDisjoint = 0, nonZeroIntersecting = 0, sumIntersecting = 0;
        for (int r = hm.rows - 1; r >= 0; r--) {
          StringBuilder sb = new StringBuilder();
          for (int c = 0; c < hm.columns; c++) {
            double minX = hm.region.getMinX() + c * cw, minY = hm.region.getMinY() + r * ch;
            Rectangle cell = ctx.getShapeFactory().rect(minX, minX + cw, minY, minY + ch);
            boolean disjoint = !cell.relate(circle).intersects();
            int n = hm.getCount(c, r);
            if (n != 0 && disjoint) nonZeroDisjoint++;
            if (n != 0 && !disjoint) nonZeroIntersecting++;
            if (!disjoint) sumIntersecting += n;
            if (c >= 10 && r >= 10) sb.append(' ').append(n).append(disjoint ? 'D' : 'i');
          }
          if (r >= 10) {
            System.out.println("PROBE " + label + " y=[" + (hm.region.getMinY() + r * ch) + "," + (hm.region.getMinY() + (r + 1) * ch) + "]" + sb);
          }
        }
        System.out.println("PROBE " + label + " nonZeroDisjointCells=" + nonZeroDisjoint + " nonZeroIntersectingCells=" + nonZeroIntersecting + " sumIntersecting=" + sumIntersecting);
      }
    }
  }
}
