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

import static org.junit.Assert.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.spatial.prefix.RecursivePrefixTreeStrategy;
import org.apache.lucene.spatial.prefix.tree.GeohashPrefixTree;
import org.apache.lucene.spatial.query.SpatialArgs;
import org.apache.lucene.spatial.query.SpatialOperation;
import org.apache.lucene.spatial3d.geom.GeoCircleFactory;
import org.apache.lucene.spatial3d.geom.PlanetModel;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.junit.Test;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Shape;

/**
 * The usage pattern of the pre-7.1 upstream Geo3dRptTest: a Geo3dShape query in the standard
 * spatial4j geo context (SpatialContext.GEO), so the geohash cells are plain spatial4j rectangles.
 * The query is a 5-degree circle at (lon 22.5, lat 22.5), which lies inside the level-1 geohash cell
 * lon [0, 45] x lat [0, 45]. A point at (40, 40) is in that cell but about 23 degrees from the circle
 * centre, so an Intersects query must not match it. The point at the centre is a control.
 * One source file for every release from 7.0 to main (plain JUnit 4, FSDirectory on a temp dir).
 */
@SuppressWarnings({"rawtypes", "unchecked", "deprecation"})
public class TestGeo3dShapeRptSameSource {

  private static int count(double lon, double lat) throws Exception {
    SpatialContext ctx = SpatialContext.GEO;
    RecursivePrefixTreeStrategy strategy =
        new RecursivePrefixTreeStrategy(new GeohashPrefixTree(ctx, 8), "geo");
    Shape circle =
        new Geo3dShape(
            GeoCircleFactory.makeGeoCircle(
                PlanetModel.SPHERE, Math.toRadians(22.5), Math.toRadians(22.5), Math.toRadians(5)),
            ctx);
    Path tmp = Files.createTempDirectory(Paths.get(System.getProperty("java.io.tmpdir")), "rpt");
    try (Directory dir = FSDirectory.open(tmp)) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        for (IndexableField f : strategy.createIndexableFields(ctx.makePoint(lon, lat))) {
          doc.add(f);
        }
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        return new IndexSearcher(r)
            .count(strategy.makeQuery(new SpatialArgs(SpatialOperation.Intersects, circle)));
      }
    }
  }

  @Test
  public void controlPointAtCircleCentreMatches() throws Exception {
    assertEquals(1, count(22.5, 22.5));
  }

  @Test
  public void farPointInSameCellDoesNotMatch() throws Exception {
    assertEquals("point (40, 40) is about 23 degrees from a 5-degree circle", 0, count(40, 40));
  }
}
