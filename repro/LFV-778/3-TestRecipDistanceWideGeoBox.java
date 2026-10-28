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
package org.apache.lucene.spatial.v1check;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.search.DoubleValues;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.spatial.SpatialStrategy;
import org.apache.lucene.spatial.bbox.BBoxStrategy;
import org.apache.lucene.spatial.prefix.RecursivePrefixTreeStrategy;
import org.apache.lucene.spatial.prefix.tree.QuadPrefixTree;
import org.apache.lucene.spatial.serialized.SerializedDVStrategy;
import org.apache.lucene.spatial.vector.PointVectorStrategy;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Point;
import org.locationtech.spatial4j.shape.Rectangle;

/**
 * Independent check of the javadoc of {@link SpatialStrategy#makeRecipDistanceValueSource}:
 *
 * <pre>
 * The formula is zScaling/(d + zScaling) where 'd' is the distance and 'zScaling' is one tenth
 * the distance to the farthest edge from the center. Thus the scores will be 1 for indexed points
 * at the center of the query shape and as low as ~0.1 at its furthest edges.
 * </pre>
 *
 * <p>For a point on the furthest edge, d = D (distance from the centre to that edge) and zScaling
 * = D/10, so the documented score is 1/11 = 0.0909. The tolerance used here is the one of the
 * upstream test TestDistanceStrategy#testRecipScore (0.10 +- 0.09).
 *
 * <p>Uses only public API. Distances (degrees) are recomputed here with an independent haversine
 * implementation, not with the spatial context's calculator.
 */
public class TestRecipDistanceWideGeoBox extends LuceneTestCase {

  private static final SpatialContext CTX = SpatialContext.GEO;
  private static final double EDGE_EXPECTED = 0.10;
  private static final double EDGE_TOLERANCE = 0.09; // same as upstream TestDistanceStrategy

  private static List<SpatialStrategy> strategies() {
    List<SpatialStrategy> list = new ArrayList<>();
    list.add(PointVectorStrategy.newInstance(CTX, "pv"));
    list.add(BBoxStrategy.newInstance(CTX, "bbox"));
    list.add(new SerializedDVStrategy(CTX, "ser"));
    list.add(new RecursivePrefixTreeStrategy(new QuadPrefixTree(CTX, 25), "rpt"));
    return list;
  }

  /** Independent great-circle distance in degrees (haversine on the unit sphere). */
  private static double gcDeg(double lon1, double lat1, double lon2, double lat2) {
    double p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2);
    double dp = p2 - p1, dl = Math.toRadians(lon2 - lon1);
    double h =
        Math.sin(dp / 2) * Math.sin(dp / 2)
            + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
    return Math.toDegrees(2 * Math.asin(Math.min(1.0, Math.sqrt(h))));
  }

  /** Recip values, one per point, in insertion order, for one strategy. */
  private double[] recipValues(SpatialStrategy s, Rectangle query, Point... pts)
      throws IOException {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig(null).setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (Point p : pts) {
          Document doc = new Document();
          for (Field f : s.createIndexableFields(p)) {
            doc.add(f);
          }
          w.addDocument(doc);
        }
        w.commit();
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(1, r.leaves().size());
        LeafReaderContext leaf = r.leaves().get(0);
        DoubleValuesSource vs = s.makeRecipDistanceValueSource(query);
        DoubleValues dv = vs.getValues(leaf, null);
        double[] out = new double[pts.length];
        for (int i = 0; i < pts.length; i++) {
          assertTrue(dv.advanceExact(i));
          out[i] = dv.doubleValue();
        }
        return out;
      }
    }
  }

  /**
   * Checks every strategy; returns a report of strategies whose score at the furthest edge point
   * is outside 0.10 +- 0.09 (empty when all comply).
   */
  private String check(Rectangle query, Point edge) throws IOException {
    Point center = query.getCenter();
    double dEdge = gcDeg(center.getX(), center.getY(), edge.getX(), edge.getY());
    double documentedEdgeScore = (0.1 * dEdge) / (dEdge + 0.1 * dEdge);
    // what the implementation actually uses: a tenth of half the corner-to-corner geodesic
    double cornerToCorner =
        gcDeg(query.getMinX(), query.getMinY(), query.getMaxX(), query.getMaxY());
    StringBuilder bad = new StringBuilder();
    for (SpatialStrategy s : strategies()) {
      double[] v = recipValues(s, query, center, edge);
      double zFromScore = v[1] * dEdge / (1 - v[1]); // invert z/(d+z)
      String line =
          String.format(
              Locale.ROOT,
              "%s query=%s centre=%s centreScore=%.5f edge=%s dEdge=%.3f edgeScore=%.5f"
                  + " (documented %.5f) impliedZScaling=%.4f documentedZScaling=%.4f"
                  + " 0.05*cornerToCornerGeodesic=%.4f%n",
              s.getClass().getSimpleName(),
              query,
              center,
              v[0],
              edge,
              dEdge,
              v[1],
              documentedEdgeScore,
              zFromScore,
              0.1 * dEdge,
              0.05 * cornerToCorner);
      System.out.print(line);
      if (Math.abs(v[1] - EDGE_EXPECTED) > EDGE_TOLERANCE) {
        bad.append(line);
      }
    }
    return bad.toString();
  }

  /** Control: a geo box narrower than 180 degrees behaves as documented (expected to pass). */
  public void testControlNarrowBox() throws IOException {
    Rectangle q = CTX.getShapeFactory().rect(-60, 60, -10, 10);
    // farthest boundary point from the centre (0,0) is a corner
    String bad = check(q, CTX.getShapeFactory().pointXY(60, 10));
    assertTrue("edge score outside the documented ~0.1:\n" + bad, bad.isEmpty());
  }

  /** A latitude band over all longitudes: 5S..5N. */
  public void testFullLongitudeBand() throws IOException {
    Rectangle q = CTX.getShapeFactory().rect(-180, 180, -5, 5);
    // centre (0,0); the band's boundary is the two latitude circles; the farthest boundary
    // point from the centre is (180, +-5), 175 degrees away
    String bad = check(q, CTX.getShapeFactory().pointXY(180, 5));
    assertTrue("edge score outside the documented ~0.1:\n" + bad, bad.isEmpty());
  }

  /** A 340-degree-wide box that does not cross the dateline. */
  public void testBox340Wide() throws IOException {
    Rectangle q = CTX.getShapeFactory().rect(-170, 170, -10, 10);
    // centre (0,0); farthest boundary point is the middle of the east edge, 170 degrees away
    String bad = check(q, CTX.getShapeFactory().pointXY(170, 0));
    assertTrue("edge score outside the documented ~0.1:\n" + bad, bad.isEmpty());
  }
}
