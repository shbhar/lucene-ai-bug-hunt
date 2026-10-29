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
 * SimpleWKTShapeParser.parse declares IOException and ParseException. parseGeometry and
 * parseGeometryCollection recurse once per nested GEOMETRYCOLLECTION with no depth bound, so a
 * deeply nested (valid) or unterminated collection lets a StackOverflowError escape.
 *
 * <p>The parse runs on a thread with a fixed 1 MiB stack so the outcome does not depend on the
 * runner's default stack size; on a default 2 MiB stack 10,000 levels also fail.
 */
public class TestWKTDeepGeometryCollection extends LuceneTestCase {

  private static final int DEPTH = 20_000;
  private static final long STACK = 1L << 20;

  private static Object parse(String wkt) throws Exception {
    Object[] out = new Object[1];
    Thread t =
        new Thread(
            null,
            () -> {
              try {
                out[0] = SimpleWKTShapeParser.parse(wkt);
              } catch (Throwable e) {
                out[0] = e;
              }
            },
            "wkt-parse",
            STACK);
    t.start();
    t.join();
    if (out[0] instanceof StackOverflowError e) {
      throw new AssertionError(
          "SimpleWKTShapeParser.parse let a StackOverflowError escape for "
              + wkt.length()
              + " chars; the declared outcome is a result, an IOException or a ParseException",
          e);
    }
    if (out[0] instanceof ParseException e) {
      throw e;
    }
    if (out[0] instanceof Throwable e) {
      throw new AssertionError("unexpected throwable", e);
    }
    return out[0];
  }

  /** Valid WKT: nested geometry collections parse to nested Object[]. */
  public void testDeeplyNestedGeometryCollection() throws Exception {
    Object small = parse("GEOMETRYCOLLECTION (".repeat(10) + "POINT (1 2)" + ")".repeat(10));
    assertTrue(small instanceof Object[]);
    try {
      Object deep =
          parse("GEOMETRYCOLLECTION (".repeat(DEPTH) + "POINT (1 2)" + ")".repeat(DEPTH));
      assertTrue(deep instanceof Object[]);
    } catch (ParseException _) {
      // declared failure mode (for example a nesting limit), acceptable for very deep input
    }
  }

  /** Malformed WKT: unterminated collections must give the declared ParseException. */
  public void testUnterminatedGeometryCollection() {
    expectThrows(ParseException.class, () -> parse("GEOMETRYCOLLECTION (".repeat(10)));
    expectThrows(ParseException.class, () -> parse("GEOMETRYCOLLECTION (".repeat(DEPTH)));
  }
}
