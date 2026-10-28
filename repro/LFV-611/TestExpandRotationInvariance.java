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

/**
 * GeoBBox.expand(angle) near the antimeridian. Only public API is used (GeoBBoxFactory.makeGeoBBox,
 * GeoBBox.expand, GeoBBox.isWithin(GeoPoint), new GeoPoint(PlanetModel, lat, lon)).
 *
 * <p>Oracle: every planet model is symmetric under rotation about the polar axis, so whether a
 * point lies in box.expand(a) must not depend on where the box sits in longitude. A probe 0.05 rad
 * beyond a longitude edge of a box expanded by 0.1 rad must be inside the expanded box (the
 * upstream TestGeoBBox.testBBoxExpand asserts exactly this for a box away from the dateline: a
 * probe 0.05 beyond the edge is inside, a probe 0.15 beyond is outside).
 */
public class TestExpandRotationInvariance extends LuceneTestCase {

  private static final double ANGLE = 0.1;
  private static final double PROBE = 0.05;

  /** Normalizes a longitude into (-PI, PI]. */
  private static double wrap(double lon) {
    while (lon > Math.PI) {
      lon -= 2.0 * Math.PI;
    }
    while (lon <= -Math.PI) {
      lon += 2.0 * Math.PI;
    }
    return lon;
  }

  // ---- minimal cases ----

  /** Control, away from the dateline: the east edge of [2.0, 2.2] moves by 0.1. */
  public void testControlEastEdgeAwayFromDateline() {
    final PlanetModel pm = PlanetModel.SPHERE;
    final GeoBBox expanded = GeoBBoxFactory.makeGeoBBox(pm, 0.2, -0.2, 2.0, 2.2).expand(ANGLE);
    assertTrue(expanded.isWithin(new GeoPoint(pm, 0.0, 2.2 + PROBE)));
    assertFalse(expanded.isWithin(new GeoPoint(pm, 0.0, 2.2 + 0.15)));
  }

  /** Same box shape rotated east by 0.9 rad: [2.9, 3.1]. Its east edge must move past +PI. */
  public void testEastEdgeExpandsAcrossDateline() {
    final PlanetModel pm = PlanetModel.SPHERE;
    final GeoBBox box = GeoBBoxFactory.makeGeoBBox(pm, 0.2, -0.2, 2.9, 3.1);
    final GeoBBox expanded = box.expand(ANGLE);
    // the west edge, away from the dateline, does move
    assertTrue(expanded.isWithin(new GeoPoint(pm, 0.0, 2.9 - PROBE)));
    // the east edge must move to 3.2 - 2 PI = -3.0832; probe at 3.15 - 2 PI = -3.1332
    final GeoPoint probe = new GeoPoint(pm, 0.0, wrap(3.1 + PROBE));
    assertTrue(
        "box=" + box + " expanded=" + expanded + " must contain lon=" + wrap(3.1 + PROBE),
        expanded.isWithin(probe));
  }

  /** Mirror image on WGS84: [-3.1, -2.9] must expand west past -PI. */
  public void testWestEdgeExpandsAcrossDatelineWgs84() {
    final PlanetModel pm = PlanetModel.WGS84;
    final GeoBBox box = GeoBBoxFactory.makeGeoBBox(pm, 0.2, -0.2, -3.1, -2.9);
    final GeoBBox expanded = box.expand(ANGLE);
    assertTrue(expanded.isWithin(new GeoPoint(pm, 0.0, -2.9 + PROBE)));
    final GeoPoint probe = new GeoPoint(pm, 0.0, wrap(-3.1 - PROBE));
    assertTrue(
        "box=" + box + " expanded=" + expanded + " must contain lon=" + wrap(-3.1 - PROBE),
        expanded.isWithin(probe));
  }

  /** The bounds of the expanded box report a right longitude of exactly PI (clamped). */
  public void testExpandedBoundsRightLongitude() {
    final PlanetModel pm = PlanetModel.SPHERE;
    final GeoBBox expanded = GeoBBoxFactory.makeGeoBBox(pm, 0.2, -0.2, 2.9, 3.1).expand(ANGLE);
    final LatLonBounds b = new LatLonBounds();
    expanded.getBounds(b);
    assertFalse(b.checkNoLongitudeBound());
    assertEquals(
        "expanded=" + expanded, wrap(3.1 + ANGLE), b.getRightLongitude(), 1e-9);
  }

  // ---- sweep over box kinds and longitudes ----

  private interface BoxMaker {
    GeoBBox make(PlanetModel pm, double centerLon);

    /** Latitude of the probe points (inside the original latitude range). */
    double probeLat();

    /** Half-width in longitude of the box. */
    double halfWidth();
  }

  private static BoxMaker maker(
      final double top, final double bottom, final double halfWidth, final double probeLat) {
    return new BoxMaker() {
      @Override
      public GeoBBox make(PlanetModel pm, double c) {
        return GeoBBoxFactory.makeGeoBBox(
            pm, top, bottom, wrap(c - halfWidth), wrap(c + halfWidth));
      }

      @Override
      public double probeLat() {
        return probeLat;
      }

      @Override
      public double halfWidth() {
        return halfWidth;
      }
    };
  }

  public void testSweepRotationInvariance() {
    final BoxMaker[] makers = {
      maker(0.2, -0.2, 0.1, 0.0), // GeoRectangle
      maker(0.2, -0.2, 1.7, 0.0), // GeoWideRectangle
      maker(Math.PI * 0.5, 0.5, 0.1, 0.7), // GeoNorthRectangle
      maker(-0.5, -Math.PI * 0.5, 0.1, -0.7), // GeoSouthRectangle
      maker(0.3, 0.3, 0.1, 0.3), // GeoDegenerateHorizontalLine
      maker(0.2, -0.2, 0.0, 0.0), // GeoDegenerateVerticalLine
      maker(0.3, 0.3, 0.0, 0.3), // GeoDegeneratePoint
      maker(Math.PI * 0.5, -Math.PI * 0.5, 0.1, 0.0), // GeoLongitudeSlice
    };
    final PlanetModel[] models = {PlanetModel.SPHERE, PlanetModel.WGS84};
    final int steps = 720;
    int checks = 0;
    int failures = 0;
    final java.util.TreeMap<String, Integer> byClass = new java.util.TreeMap<>();
    final StringBuilder first = new StringBuilder();
    for (PlanetModel pm : models) {
      for (BoxMaker m : makers) {
        for (int i = 0; i < steps; i++) {
          final double c = -Math.PI + (i + 0.5) * (2.0 * Math.PI / steps);
          final GeoBBox box = m.make(pm, c);
          final GeoBBox expanded = box.expand(ANGLE);
          final double[] probeLons = {
            wrap(c + m.halfWidth() + PROBE), wrap(c - m.halfWidth() - PROBE)
          };
          for (double plon : probeLons) {
            checks++;
            final GeoPoint p = new GeoPoint(pm, m.probeLat(), plon);
            if (expanded.isWithin(p) == false) {
              failures++;
              byClass.merge(box.getClass().getSimpleName(), 1, Integer::sum);
              if (first.length() < 1500) {
                first
                    .append("\n  center=")
                    .append(c)
                    .append(" probeLon=")
                    .append(plon)
                    .append(" box=")
                    .append(box)
                    .append(" -> expanded=")
                    .append(expanded);
              }
            }
          }
        }
      }
    }
    assertEquals(
        "checks=" + checks + " failures by original class=" + byClass + first, 0, failures);
  }
}
