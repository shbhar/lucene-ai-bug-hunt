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
import org.locationtech.spatial4j.shape.Shape;
import org.locationtech.spatial4j.shape.SpatialRelation;

public class TestGeo3dShapeRelateWorldBounds extends LuceneTestCase {

  public void testCircleIsWithinWorldBounds() {
    SpatialContext ctx = new Geo3dSpatialContextFactory().newSpatialContext();
    Shape circle = ctx.getShapeFactory().circle(0, 0, 10);
    // expected: a 10-degree circle is WITHIN the world bounds; actual: CONTAINS
    assertEquals(SpatialRelation.WITHIN, circle.relate(ctx.getWorldBounds()));
  }
}
