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

import java.text.ParseException;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * SimpleGeoJSONPolygonParser checks that "coordinates" (and every element of a MultiPolygon's
 * coordinates) is an array and throws ParseException "... should be an array, but got: " +
 * o.getClass() otherwise. A JSON null, or an object (which the parser represents as null), makes
 * o.getClass() throw NullPointerException while the message is built, so the declared
 * ParseException becomes a NullPointerException.
 */
public class TestGeoJSONNullCoordinates extends LuceneTestCase {

  public void testNonArrayCoordinatesControl() {
    // the check works for a string value
    expectThrows(
        ParseException.class,
        () -> Polygon.fromGeoJSON("{\"type\":\"Polygon\",\"coordinates\":\"x\"}"));
  }

  public void testNullCoordinates() {
    expectThrows(
        ParseException.class,
        () -> Polygon.fromGeoJSON("{\"type\":\"Polygon\",\"coordinates\":null}"));
  }

  public void testObjectCoordinates() {
    expectThrows(
        ParseException.class, () -> Polygon.fromGeoJSON("{\"type\":\"Polygon\",\"coordinates\":{}}"));
  }

  public void testMultiPolygonObjectElement() {
    expectThrows(
        ParseException.class,
        () -> Polygon.fromGeoJSON("{\"type\":\"MultiPolygon\",\"coordinates\":[{}]}"));
  }
}
