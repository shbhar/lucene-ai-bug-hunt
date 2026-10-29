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
import org.locationtech.spatial4j.shape.ShapeFactory;
import org.locationtech.spatial4j.shape.SpatialRelation;

public class TestGeo3dLineStringBuffer extends LuceneTestCase {

  public void testBufferIsInDegrees() {
    ShapeFactory f = new Geo3dSpatialContextFactory().newSpatialContext().getShapeFactory();
    // expected: a line buffered by 1 degree is DISJOINT from a point 20 degrees away; actual: CONTAINS
    assertEquals(
        SpatialRelation.DISJOINT,
        f.lineString().pointXY(0, 0).pointXY(10, 0).buffer(1).build().relate(f.pointXY(5, 20)));
  }
}
