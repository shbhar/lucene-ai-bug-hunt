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
 * Polygon.fromGeoJSON declares only ParseException. SimpleGeoJSONPolygonParser.parseArray and
 * parseObject recurse once per nesting level with no depth bound, so deeply nested JSON lets a
 * StackOverflowError escape, even for a valid GeoJSON Feature whose properties hold a nested array
 * or object (GeoJSON allows any JSON value as a property).
 *
 * <p>The parse runs on a thread with a fixed 1 MiB stack so the outcome does not depend on the
 * runner's default stack size; on a default 2 MiB stack 10,000 levels (20 KB of text) also fail.
 */
public class TestGeoJSONDeepNesting extends LuceneTestCase {

  private static final int DEPTH = 50_000;
  private static final long STACK = 1L << 20;
  private static final String POLYGON =
      "{\"type\":\"Polygon\",\"coordinates\":"
          + "[[[100.0,0.0],[101.0,0.0],[101.0,1.0],[100.0,1.0],[100.0,0.0]]]}";

  /** Runs fromGeoJSON on its own thread; returns the result, or rethrows ParseException. */
  private static Polygon[] parse(String geojson) throws Exception {
    Object[] out = new Object[1];
    Thread t =
        new Thread(
            null,
            () -> {
              try {
                out[0] = Polygon.fromGeoJSON(geojson);
              } catch (Throwable e) {
                out[0] = e;
              }
            },
            "geojson-parse",
            STACK);
    t.start();
    t.join();
    if (out[0] instanceof StackOverflowError e) {
      throw new AssertionError(
          "Polygon.fromGeoJSON let a StackOverflowError escape for "
              + geojson.length()
              + " chars; the declared outcome is a result or a ParseException",
          e);
    }
    if (out[0] instanceof ParseException e) {
      throw e;
    }
    if (out[0] instanceof Throwable e) {
      throw new AssertionError("unexpected throwable", e);
    }
    return (Polygon[]) out[0];
  }

  private static String feature(String propertyValue) {
    return "{\"type\":\"Feature\",\"geometry\":"
        + POLYGON
        + ",\"properties\":{\"p\":"
        + propertyValue
        + "}}";
  }

  /** The polygon, or the declared ParseException (for example a nesting limit); never a VM error. */
  private static void assertPolygonOrParseException(String geojson) throws Exception {
    try {
      assertEquals(1, parse(geojson).length);
    } catch (ParseException _) {
      // declared failure mode, acceptable for very deep input
    }
  }

  /** Valid GeoJSON: a Feature whose property is a deeply nested (empty) array. */
  public void testValidFeatureWithDeeplyNestedArrayProperty() throws Exception {
    assertEquals(1, parse(feature("[".repeat(10) + "]".repeat(10))).length);
    assertPolygonOrParseException(feature("[".repeat(DEPTH) + "]".repeat(DEPTH)));
  }

  /** Valid GeoJSON: a Feature whose property is a deeply nested object. */
  public void testValidFeatureWithDeeplyNestedObjectProperty() throws Exception {
    assertEquals(1, parse(feature("{\"a\":".repeat(10) + "1" + "}".repeat(10))).length);
    assertPolygonOrParseException(feature("{\"a\":".repeat(DEPTH) + "1" + "}".repeat(DEPTH)));
  }

  /** Malformed GeoJSON: unterminated arrays must give the declared ParseException. */
  public void testUnterminatedArrays() {
    expectThrows(
        ParseException.class,
        () -> parse("{\"type\":\"Polygon\",\"coordinates\":" + "[".repeat(10)));
    expectThrows(
        ParseException.class,
        () -> parse("{\"type\":\"Polygon\",\"coordinates\":" + "[".repeat(DEPTH)));
  }
}
