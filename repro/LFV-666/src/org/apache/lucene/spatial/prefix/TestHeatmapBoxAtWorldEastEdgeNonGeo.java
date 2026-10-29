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

import java.util.Map;
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.spatial.prefix.tree.QuadPrefixTree;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.context.SpatialContextFactory;
import org.locationtech.spatial4j.shape.Rectangle;
import org.locationtech.spatial4j.shape.SpatialRelation;

public class TestHeatmapBoxAtWorldEastEdgeNonGeo extends LuceneTestCase {

  public void testBoxEndingAtWorldEastEdge() throws Exception {
    SpatialContext ctx =
        SpatialContextFactory.makeSpatialContext(
            Map.of(
                "geo", "false",
                "worldBounds", "ENVELOPE(-20037508.34, 20037508.34, 20037508.34, -20037508.34)"),
            null);
    PrefixTreeStrategy strategy = new RecursivePrefixTreeStrategy(new QuadPrefixTree(ctx), "f");
    Rectangle box = ctx.getShapeFactory().rect(3_000_000, 20037508.34, 0, 1_000_000);
    // expected: a heatmap covering the box; actual: InvalidShapeException "maxX must be >= minX"
    HeatmapFacetCounter.Heatmap heatmap =
        strategy.calcFacets(new MultiReader().getContext(), null, box, 5, 1000);
    assertEquals(SpatialRelation.CONTAINS, heatmap.region.relate(box));
  }
}
