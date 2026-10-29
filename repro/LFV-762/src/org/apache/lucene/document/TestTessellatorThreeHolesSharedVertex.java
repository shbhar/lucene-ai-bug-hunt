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
package org.apache.lucene.document;

import org.apache.lucene.geo.Polygon;
import org.apache.lucene.geo.SimpleWKTShapeParser;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestTessellatorThreeHolesSharedVertex extends LuceneTestCase {

  public void testComplexPolygon41WithLastHoleFirst() throws Exception {
    // TestTessellator#testComplexPolygon41 (LUCENE-9400), its third hole moved to the front
    String wkt =
        "POLYGON((-1.569137181294115 54.4855283059375, -1.5692505240440333 54.48535373128068, -1.5684753656387294 54.48534438253056, -1.568606793880459 54.485674703738624, -1.5694141387939453 54.48611720532629, -1.569137181294115 54.4855283059375),"
            + "(-1.5689449291711688 54.48547663706703,  -1.5689879483854345 54.485580118416785, -1.5687756358893499 54.485612860811244, -1.568765285875931 54.485496217554285, -1.5689449291711688 54.48547663706703),"
            + "(-1.569137181294115 54.4855283059375, -1.5690783030431206 54.48545352137167, -1.5689449291711688 54.48547663706703, -1.569137181294115 54.4855283059375),"
            + "(-1.5689449291711688 54.48547663706703, -1.5689437289004642 54.48535482680399, -1.5687730514221028 54.48538045082698, -1.5689449291711688 54.48547663706703))";
    Polygon polygon = (Polygon) SimpleWKTShapeParser.parse(wkt);
    try {
      LatLonShape.createIndexableFields("shape", polygon);
    } catch (IllegalArgumentException e) {
      fail("expected: indexes as with the original hole order; actual: " + e.getMessage());
    }
  }
}
