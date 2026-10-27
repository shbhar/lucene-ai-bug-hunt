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
package org.apache.lucene.document;

import org.apache.lucene.geo.Circle;
import org.apache.lucene.geo.GeoEncodingUtils;
import org.apache.lucene.geo.Rectangle;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.SloppyMath;

/**
 * A legal circle (centre latitude about 0.44 degrees, radius about 9,959 km) whose northern edge
 * stops just short of the north pole: Rectangle.fromPointDistance takes its non-pole branch, where
 * asin(sin(r) / cos(lat)) has an argument below 1 in exact arithmetic, but SloppyMath.cos is
 * evaluated one ulp higher at the larger angle (a table-knot switch), so the argument is
 * 1.0000000000000002, asin returns NaN and the bounding box is rejected with
 * "invalid longitude NaN". Every distance query on such a circle throws instead of matching.
 */
public class TestDistanceQueryBoxNaN extends LuceneTestCase {

  // centre latitude just below 2.5 * 360 / 2048 degrees (a SloppyMath.cos table-knot switch)
  static final double LAT = Double.longBitsToDouble(0x3fdc1fffffffffffL); // 0.43945312499999994
  static final double LON = 0;
  static final double RADIUS = Double.longBitsToDouble(0x4162fea48296123fL); // 9958692.080819247 m

  public void testBoundingBoxOfLegalCircle() {
    // fromPointDistance's own preconditions hold: the circle does not reach either pole
    double radLat = Math.toRadians(LAT);
    double radDistance = (RADIUS + 7E-2) / 6_371_008.7714;
    assertTrue(radLat + radDistance < Math.PI / 2);
    assertTrue(radLat - radDistance > -Math.PI / 2);
    Rectangle box = Rectangle.fromPointDistance(LAT, LON, RADIUS); // throws IllegalArgumentException
    assertTrue(box.minLat <= LAT && LAT <= box.maxLat);
  }

  public void testDistanceQueryMatchesItsCentre() throws Exception {
    try (Directory dir = newDirectory()) {
      double[][] points = {{LAT, LON}, {0, 45}, {-30, 170}};
      int expected = 0;
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (double[] p : points) {
          Document doc = new Document();
          doc.add(new LatLonPoint("p", p[0], p[1]));
          doc.add(new LatLonDocValuesField("p", p[0], p[1]));
          w.addDocument(doc);
          double qLat = GeoEncodingUtils.decodeLatitude(GeoEncodingUtils.encodeLatitude(p[0]));
          double qLon = GeoEncodingUtils.decodeLongitude(GeoEncodingUtils.encodeLongitude(p[1]));
          if (SloppyMath.haversinMeters(LAT, LON, qLat, qLon) <= RADIUS) {
            expected++;
          }
        }
      }
      assertEquals(2, expected); // the centre and (0, 45) are inside, (-30, 170) is not
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        Query q = LatLonPoint.newDistanceQuery("p", LAT, LON, RADIUS);
        assertEquals(expected, s.count(q)); // throws IllegalArgumentException: invalid longitude NaN
      }
    }
  }

  public void testSlowDistanceQueryOnLegalCircle() {
    // the doc-values and geometry factories build the same box when the query is constructed
    Query q = LatLonDocValuesField.newSlowDistanceQuery("p", LAT, LON, RADIUS);
    assertNotNull(q);
    Query g =
        LatLonPoint.newGeometryQuery(
            "p", ShapeField.QueryRelation.INTERSECTS, new Circle(LAT, LON, RADIUS));
    assertNotNull(g);
  }
}
