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

import org.apache.lucene.search.SortField;
import org.apache.lucene.spatial3d.geom.PlanetModel;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestGeo3DOutsideSortFieldEquals extends LuceneTestCase {

  public void testIdenticalOutsideDistanceSortsAreEqual() {
    SortField a =
        Geo3DDocValuesField.newOutsideDistanceSort("loc", 10, 20, 1000, PlanetModel.WGS84);
    SortField b =
        Geo3DDocValuesField.newOutsideDistanceSort("loc", 10, 20, 1000, PlanetModel.WGS84);
    // expected: a.equals(b) is true; actual: ClassCastException (cast to Geo3DPointSortField)
    try {
      assertEquals(a, b);
    } catch (ClassCastException e) {
      throw new AssertionError("expected a.equals(b) to return true, got " + e, e);
    }
  }
}
