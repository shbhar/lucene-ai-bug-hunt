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
 * SimpleGeoJSONPolygonParser passes every parseArray/parseObject level its own "path" String
 * (path + "." + key, path + ".[]"), and all of them stay reachable from the live frames. A valid
 * Feature whose properties hold one long key mapped to a nested array therefore needs about
 * depth x key-length bytes of heap: here 900 nested arrays (well inside any stack, and inside a
 * typical nesting limit of 1,000) and a key of 1.25 x maxMemory / 900 chars, so the text is about
 * 0.14% of the heap and the parse still runs out of memory. The declared outcomes are the polygon or
 * a ParseException.
 */
public class TestGeoJSONPathMemory extends LuceneTestCase {

  private static final int DEPTH = 900;

  public void testValidFeatureWithLongKeyAndModerateNesting() throws Exception {
    long maxMemory = Runtime.getRuntime().maxMemory();
    int keyLength = (int) Math.min(64L << 20, maxMemory / DEPTH * 5 / 4);
    String geojson =
        "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Polygon\",\"coordinates\":"
            + "[[[100.0,0.0],[101.0,0.0],[101.0,1.0],[100.0,1.0],[100.0,0.0]]]},"
            + "\"properties\":{\""
            + "k".repeat(keyLength)
            + "\":"
            + "[".repeat(DEPTH)
            + "]".repeat(DEPTH)
            + "}}";
    Object[] out = new Object[1];
    // a large stack so that recursion depth cannot be the cause (1,000 levels never overflow)
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
            64L << 20);
    t.start();
    t.join();
    if (out[0] instanceof OutOfMemoryError e) {
      throw new AssertionError(
          "Polygon.fromGeoJSON ran out of memory on a valid "
              + geojson.length()
              + "-char Feature (nesting depth "
              + DEPTH
              + ") with maxMemory="
              + (maxMemory >> 20)
              + " MiB",
          e);
    }
    if (out[0] instanceof ParseException) {
      return; // declared failure mode
    }
    if (out[0] instanceof Throwable e) {
      throw new AssertionError("unexpected throwable", e);
    }
    assertEquals(1, ((Polygon[]) out[0]).length);
  }
}
