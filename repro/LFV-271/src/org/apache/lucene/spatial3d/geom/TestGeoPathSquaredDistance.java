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
 * For a path, DistanceStyle.LINEAR_SQUARED aggregates the square roots of the per-piece squared
 * distances and squares the sum (DistanceStyle#toAggregationForm / #fromAggregationForm), so it
 * must equal the square of the DistanceStyle.LINEAR path distance; likewise NORMAL_SQUARED versus
 * NORMAL. That holds for points in an end cap, but for a point inside a path segment
 * GeoStandardPath returns the square of the expected value.
 */
public class TestGeoPathSquaredDistance extends LuceneTestCase {

  public void testSquaredStylesMatchSquareOfLinearStyles() {
    final PlanetModel pm = PlanetModel.SPHERE;
    final GeoPath path =
        GeoPathFactory.makeGeoPath(
            pm, 0.1, new GeoPoint[] {new GeoPoint(pm, 0.0, 0.0), new GeoPoint(pm, 0.0, 0.5)});

    // Control: a point in the start cap (before the first path point) is consistent.
    final GeoPoint inCap = new GeoPoint(pm, 0.0, -0.05);
    assertTrue(path.isWithin(inCap));
    final double capLinear = path.computeDistance(DistanceStyle.LINEAR, inCap);
    assertEquals(capLinear * capLinear, path.computeDistance(DistanceStyle.LINEAR_SQUARED, inCap), 1e-12);

    // A point on the segment between the two path points.
    final GeoPoint onSegment = new GeoPoint(pm, 0.05, 0.25);
    assertTrue(path.isWithin(onSegment));
    final double linear = path.computeDistance(DistanceStyle.LINEAR, onSegment);
    final double linearSquared = path.computeDistance(DistanceStyle.LINEAR_SQUARED, onSegment);
    final double normal = path.computeDistance(DistanceStyle.NORMAL, onSegment);
    final double normalSquared = path.computeDistance(DistanceStyle.NORMAL_SQUARED, onSegment);
    assertEquals("LINEAR=" + linear + " LINEAR_SQUARED=" + linearSquared, linear * linear, linearSquared, 1e-12);
    assertEquals("NORMAL=" + normal + " NORMAL_SQUARED=" + normalSquared, normal * normal, normalSquared, 1e-12);
  }
}
