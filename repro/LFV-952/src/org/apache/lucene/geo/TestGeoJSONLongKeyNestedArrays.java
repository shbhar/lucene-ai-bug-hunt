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
package org.apache.lucene.geo;

import org.apache.lucene.tests.util.LuceneTestCase;

public class TestGeoJSONLongKeyNestedArrays extends LuceneTestCase {

  public void testLongPropertyKeyOver900NestedArrays() throws Exception {
    String geojson =
        "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Polygon\",\"coordinates\":"
            + "[[[0.0,0.0],[1.0,0.0],[1.0,1.0],[0.0,0.0]]]},\"properties\":{\""
            + "k".repeat(750_000)
            + "\":"
            + "[".repeat(900)
            + "]".repeat(900)
            + "}}";
    try {
      assertEquals(1, Polygon.fromGeoJSON(geojson).length);
    } catch (OutOfMemoryError e) {
      // Expected: the polygon of this ~750 KB Feature; actual: OutOfMemoryError in a 512 MiB heap.
      throw new AssertionError(
          "fromGeoJSON of a "
              + geojson.length()
              + "-char Feature ran out of a "
              + (Runtime.getRuntime().maxMemory() >> 20)
              + " MiB heap: "
              + e,
          e);
    }
  }
}
