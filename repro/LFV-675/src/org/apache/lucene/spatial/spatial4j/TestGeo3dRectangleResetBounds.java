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
package org.apache.lucene.spatial.spatial4j;

import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Rectangle;

public class TestGeo3dRectangleResetBounds extends LuceneTestCase {

  public void testResetReplacesMinX() {
    SpatialContext ctx = new Geo3dSpatialContextFactory().newSpatialContext();
    Rectangle r = ctx.getShapeFactory().rect(-50, -40, -30, -20);
    r.reset(10, 20, 30, 40);
    // expected: getMinX() is the reset value 10; actual: the old value -50
    assertEquals(10, r.getMinX(), 0);
  }
}
