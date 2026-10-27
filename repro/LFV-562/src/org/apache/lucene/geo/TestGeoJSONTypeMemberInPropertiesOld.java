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

import org.apache.lucene.util.LuceneTestCase;

/**
 * A GeoJSON Feature may carry any JSON object as "properties", including a member named "type"
 * (for example {"type": "multipolygon"} on map data). Polygon.fromGeoJSON accepts Features but
 * treats every "type" member outside "crs" as the geometry type and rejects the input.
 */
public class TestGeoJSONTypeMemberInPropertiesOld extends LuceneTestCase {

  private static final String GEOMETRY =
      "{\"type\": \"Polygon\", \"coordinates\": [[[100.0, 0.0], [101.0, 0.0], [101.0, 1.0], [100.0, 1.0], [100.0, 0.0]]]}";

  private static String feature(String properties) {
    return "{\"type\": \"Feature\", \"geometry\": " + GEOMETRY + ", \"properties\": " + properties + "}";
  }

  public void testControlPropertiesWithoutType() throws Exception {
    // accepted today
    Polygon[] expected = Polygon.fromGeoJSON(GEOMETRY);
    assertArrayEquals(expected, Polygon.fromGeoJSON(feature("{\"name\": \"park\", \"nested\": {\"a\": 1}}")));
  }

  public void testTypeProperty() throws Exception {
    Polygon[] expected = Polygon.fromGeoJSON(GEOMETRY);
    assertArrayEquals(expected, Polygon.fromGeoJSON(feature("{\"type\": \"multipolygon\"}")));
  }

  public void testTypeInNestedPropertyObject() throws Exception {
    Polygon[] expected = Polygon.fromGeoJSON(GEOMETRY);
    assertArrayEquals(
        expected, Polygon.fromGeoJSON(feature("{\"source\": {\"type\": \"survey\", \"year\": 2020}}")));
  }
}
