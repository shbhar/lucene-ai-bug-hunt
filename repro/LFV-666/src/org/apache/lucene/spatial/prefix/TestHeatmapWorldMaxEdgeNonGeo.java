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
import org.locationtech.spatial4j.shape.Rectangle;
import org.locationtech.spatial4j.shape.SpatialRelation;
import org.locationtech.spatial4j.shape.impl.RectangleImpl;

/**
 * A heatmap over a non-geo world with projected (Web Mercator) bounds, for a box that ends at the
 * world's east edge. HeatmapFacetCounter.calcFacets accepts any input shape inside the world and
 * returns a grid of cells covering it ("The actual heatmap area will usually be larger since the
 * cells on the edge that overlap are returned").
 */
public class TestHeatmapWorldMaxEdgeNonGeo extends LuceneTestCase {

  public void testBoxEndingAtWorldEastEdge() throws Exception {
    final double half = Math.PI * 6378137; // 20037508.342789244, the Web Mercator half-extent
    SpatialContextFactory factory = new SpatialContextFactory();
    factory.geo = false;
    factory.worldBounds = new RectangleImpl(-half, half, -half, half, null);
    SpatialContext ctx = factory.newSpatialContext();
    RecursivePrefixTreeStrategy strategy =
        new RecursivePrefixTreeStrategy(new QuadPrefixTree(ctx), "geo"); // default 12 levels

    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        for (Field f : strategy.createIndexableFields(ctx.getShapeFactory().pointXY(19e6, 5e5))) {
          doc.add(f);
        }
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        // the box x in [5,000 km, east edge], y in [0, 1,000 km]; facet level 4 (16 x 16 cells)
        Rectangle box = ctx.getShapeFactory().rect(5e6, half, 0, 1e6);
        HeatmapFacetCounter.Heatmap heatmap =
            strategy.calcFacets(reader.getContext(), null, box, 4, 1000);
        assertEquals(SpatialRelation.CONTAINS, heatmap.region.relate(box));
        int total = 0;
        for (int count : heatmap.counts) {
          total += count;
        }
        assertEquals("the one point inside the box", 1, total);
      }
    }
  }
}
