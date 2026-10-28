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
package org.apache.lucene.spatial3d.geom;

import org.apache.lucene.tests.util.LuceneTestCase;

/** Verifier: independent check over all plane-based distance styles, with controls. */
public class TestExactCircleJointStyles extends LuceneTestCase {

  private static final DistanceStyle[] STYLES = {
    DistanceStyle.ARC,
    DistanceStyle.LINEAR,
    DistanceStyle.LINEAR_SQUARED,
    DistanceStyle.NORMAL,
    DistanceStyle.NORMAL_SQUARED
  };
  private static final String[] NAMES = {"ARC", "LINEAR", "LINEAR_SQ", "NORMAL", "NORMAL_SQ"};

  /** WGS84, a different centre/radius than the candidate; point due north on the centre meridian. */
  public void testWgs84DueNorthAllStyles() {
    PlanetModel pm = PlanetModel.WGS84;
    double lat = -0.6, lon = 1.1, r = 0.05; // about 318 km
    GeoCircle exact = GeoCircleFactory.makeExactGeoCircle(pm, lat, lon, r, 1e-7);
    GeoCircle standard = GeoCircleFactory.makeGeoCircle(pm, lat, lon, r);
    GeoPoint q = new GeoPoint(pm, lat + r + 0.03, lon);
    assertFalse(exact.isWithin(q));
    // the exact circle's own north cardinal point (slice joint on WGS84)
    GeoPoint north = pm.surfacePointOnBearing(new GeoPoint(pm, lat, lon), r, 0.0);
    StringBuilder sb = new StringBuilder();
    boolean bad = false;
    for (int i = 0; i < STYLES.length; i++) {
      double e = exact.computeOutsideDistance(STYLES[i], q);
      double s = standard.computeOutsideDistance(STYLES[i], q);
      double toNorth = STYLES[i].computeDistance(north, q.x, q.y, q.z);
      sb.append(String.format(java.util.Locale.ROOT, " [%s exact=%s standard=%s toNorthPoint=%.6g]", NAMES[i], e, s, toNorth));
      if (!(e <= toNorth * 1.01 + 1e-9)) bad = true;
    }
    System.out.println("WGS84 due north:" + sb);
    assertFalse("exact-circle outside distance exceeds distance to its own north point:" + sb, bad);
  }

  /** Control: same circle, bearing well away from any cardinal joint. */
  public void testWgs84OffJointControl() {
    PlanetModel pm = PlanetModel.WGS84;
    double lat = -0.6, lon = 1.1, r = 0.05;
    GeoCircle exact = GeoCircleFactory.makeExactGeoCircle(pm, lat, lon, r, 1e-7);
    GeoPoint c = new GeoPoint(pm, lat, lon);
    GeoPoint edge = pm.surfacePointOnBearing(c, r, 0.37);
    GeoPoint q = pm.surfacePointOnBearing(c, r + 0.03, 0.37);
    double e = exact.computeOutsideDistance(DistanceStyle.ARC, q);
    double toEdge = edge.arcDistance(q);
    System.out.println("WGS84 bearing 0.37: exact=" + e + " toEdgeSample=" + toEdge);
    assertTrue("control: finite and close to edge distance", e <= toEdge + 1e-4 && e >= toEdge - 1e-2);
  }

  /** Sweep bearings around the north joint: report how wide the +Infinity wedge is. */
  public void testWgs84WedgeWidth() {
    PlanetModel pm = PlanetModel.WGS84;
    double lat = -0.6, lon = 1.1, r = 0.05;
    GeoCircle exact = GeoCircleFactory.makeExactGeoCircle(pm, lat, lon, r, 1e-7);
    GeoPoint c = new GeoPoint(pm, lat, lon);
    int inf = 0, n = 0;
    StringBuilder sb = new StringBuilder();
    for (double b = -0.02; b <= 0.02 + 1e-12; b += 0.002) {
      GeoPoint q = pm.surfacePointOnBearing(c, r + 0.03, b);
      double e = exact.computeOutsideDistance(DistanceStyle.ARC, q);
      n++;
      if (Double.isInfinite(e)) inf++;
      sb.append(String.format(java.util.Locale.ROOT, " %.3f:%s", b, Double.isInfinite(e) ? "Inf" : String.format(java.util.Locale.ROOT, "%.5f", e)));
    }
    System.out.println("WGS84 sweep around north joint (bearing:dist):" + sb + " -> " + inf + "/" + n + " Infinity");
    assertEquals("no +Infinity near the joint", 0, inf);
  }

  /** SPHERE: a large exact circle has no rounding issue; small one returns the far side. */
  public void testSphereSmallVsLarge() {
    PlanetModel pm = PlanetModel.SPHERE;
    for (double r : new double[] {1e-4, 1e-2}) {
      GeoCircle exact = GeoCircleFactory.makeExactGeoCircle(pm, 0.0, 0.2, r, 1e-6);
      GeoPoint q = new GeoPoint(pm, 0.0, 0.2 - r - 1e-3);
      double e = exact.computeOutsideDistance(DistanceStyle.ARC, q);
      System.out.println("SPHERE r=" + r + " west point 1e-3 outside: exact=" + e + " expected=1e-3 (+2r would be " + (1e-3 + 2 * r) + ")");
    }
    GeoCircle exact = GeoCircleFactory.makeExactGeoCircle(pm, 0.0, 0.2, 1e-4, 1e-6);
    GeoPoint q = new GeoPoint(pm, 0.0, 0.2 - 1e-4 - 1e-3);
    assertEquals(1e-3, exact.computeOutsideDistance(DistanceStyle.ARC, q), 1e-6);
  }
}
