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

/**
 * In JSON any character of a string may be written as a \\uXXXX escape: "\\u0050olygon" is the
 * string "Polygon". SimpleGeoJSONPolygonParser has code for this escape, but it appends the code
 * point as a decimal number and then reads the four hex digits again as ordinary characters.
 */
public class TestGeoJSONUnicodeEscape extends LuceneTestCase {

  private static final String COORDS =
      "[[[100.0, 0.0], [101.0, 0.0], [101.0, 1.0], [100.0, 1.0], [100.0, 0.0]]]";

  public void testControlNoEscape() throws Exception {
    assertEquals(1, Polygon.fromGeoJSON("{\"type\": \"Polygon\", \"coordinates\": " + COORDS + "}").length);
  }

  public void testEscapedLetterInTypeValue() throws Exception {
    // "\u0050" is 'P'
    Polygon[] expected = Polygon.fromGeoJSON("{\"type\": \"Polygon\", \"coordinates\": " + COORDS + "}");
    assertArrayEquals(
        expected, Polygon.fromGeoJSON("{\"type\": \"\\u0050olygon\", \"coordinates\": " + COORDS + "}"));
  }

  public void testEscapedLetterInKey() throws Exception {
    // "\u0074" is 't'
    Polygon[] expected = Polygon.fromGeoJSON("{\"type\": \"Polygon\", \"coordinates\": " + COORDS + "}");
    assertArrayEquals(
        expected, Polygon.fromGeoJSON("{\"\\u0074ype\": \"Polygon\", \"coordinates\": " + COORDS + "}"));
  }
}
