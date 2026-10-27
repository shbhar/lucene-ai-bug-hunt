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
package org.apache.lucene.spatial3d;

import org.apache.lucene.geo.Polygon;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.spatial3d.geom.PlanetModel;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The "outside distance" sort fields returned by Geo3DDocValuesField.newOutside*Sort must honour
 * the Object.equals contract (reflexive, symmetric, never throwing) just like the "distance" sort
 * fields from Geo3DDocValuesField.newDistanceSort / newPolygonSort / ... do. Only public API is used.
 */
public class TestGeo3DOutsideSortFieldEquals extends LuceneTestCase {

  /** Control: the inside-distance sort field compares fine. */
  public void testDistanceSortFieldEqualsControl() {
    SortField a = Geo3DDocValuesField.newDistanceSort("loc", 10, 20, 1000, PlanetModel.WGS84);
    SortField b = Geo3DDocValuesField.newDistanceSort("loc", 10, 20, 1000, PlanetModel.WGS84);
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
  }

  /** Two identical outside-distance sorts must be equal (and must not throw). */
  public void testOutsideDistanceSortFieldEqualsIdentical() {
    SortField a =
        Geo3DDocValuesField.newOutsideDistanceSort("loc", 10, 20, 1000, PlanetModel.WGS84);
    SortField b =
        Geo3DDocValuesField.newOutsideDistanceSort("loc", 10, 20, 1000, PlanetModel.WGS84);
    assertEquals(a.hashCode(), b.hashCode());
    assertTrue("identical outside-distance sort fields must be equal", a.equals(b));
  }

  /** Two different outside-box sorts on the same field must be unequal (and must not throw). */
  public void testOutsideBoxSortFieldEqualsDifferent() {
    SortField a = Geo3DDocValuesField.newOutsideBoxSort("loc", 0, 1, 0, 1, PlanetModel.WGS84);
    SortField b = Geo3DDocValuesField.newOutsideBoxSort("loc", 0, 2, 0, 2, PlanetModel.WGS84);
    assertFalse(a.equals(b));
  }

  /** The same failure through Sort.equals, which compares its SortField array element-wise. */
  public void testSortEqualsWithOutsidePolygonSort() {
    Polygon p = new Polygon(new double[] {0, 0, 1, 1, 0}, new double[] {0, 1, 1, 0, 0});
    Sort a = new Sort(Geo3DDocValuesField.newOutsidePolygonSort("loc", PlanetModel.WGS84, p));
    Sort b = new Sort(Geo3DDocValuesField.newOutsidePolygonSort("loc", PlanetModel.WGS84, p));
    assertEquals(a, b);
  }
}
