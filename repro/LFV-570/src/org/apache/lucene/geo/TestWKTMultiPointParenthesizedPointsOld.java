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
 * MULTIPOINT with each point in its own parentheses, e.g. {@code MULTIPOINT ((1 2), (3 4))}, is
 * the form of the WKT grammar; SimpleWKTShapeParser accepts it for one point but rejects it for
 * two or more.
 */
public class TestWKTMultiPointParenthesizedPointsOld extends LuceneTestCase {

  private static double[][] parse(String wkt) throws Exception {
    return (double[][]) SimpleWKTShapeParser.parse(wkt);
  }

  public void testOneParenthesizedPoint() throws Exception {
    // control: accepted today
    assertArrayEquals(new double[][] {{1, 2}}, parse("MULTIPOINT ((1 2))"));
  }

  public void testUnparenthesizedPoints() throws Exception {
    // control: accepted today
    assertArrayEquals(new double[][] {{1, 2}, {3, 4}}, parse("MULTIPOINT (1 2, 3 4)"));
  }

  public void testTwoParenthesizedPoints() throws Exception {
    assertArrayEquals(new double[][] {{1, 2}, {3, 4}}, parse("MULTIPOINT ((1 2), (3 4))"));
  }

  public void testParenthesizedPointsInCollection() throws Exception {
    Object[] collection =
        (Object[]) SimpleWKTShapeParser.parse("GEOMETRYCOLLECTION (MULTIPOINT ((1 2), (3 4)), POINT (5 6))");
    assertArrayEquals(new double[][] {{1, 2}, {3, 4}}, (double[][]) collection[0]);
  }
}
