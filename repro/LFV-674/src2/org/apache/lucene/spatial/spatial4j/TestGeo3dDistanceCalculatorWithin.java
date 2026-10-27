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

import org.apache.lucene.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.distance.DistanceCalculator;
import org.locationtech.spatial4j.shape.Point;

/**
 * Geo3dDistanceCalculator.within(from, toX, toY, d) returns d < distance(from, to), the negation of
 * the DistanceCalculator contract "true if the distance between from and to is <= distance".
 */
public class TestGeo3dDistanceCalculatorWithin extends LuceneTestCase {

  public void testWithinAgreesWithDistance() {
    SpatialContext ctx = new Geo3dSpatialContextFactory().newSpatialContext();
    DistanceCalculator calc = ctx.getDistCalc();
    assertTrue(calc instanceof Geo3dDistanceCalculator);
    Point from = ctx.getShapeFactory().pointXY(0, 0);
    assertEquals(1.0, calc.distance(from, 1, 0), 1e-9);
    assertEquals(50.0, calc.distance(from, 50, 0), 1e-9);
    assertTrue("(1,0) is 1 degree away, within 10 degrees", calc.within(from, 1, 0, 10));
    assertFalse("(50,0) is 50 degrees away, not within 10 degrees", calc.within(from, 50, 0, 10));
  }
}
