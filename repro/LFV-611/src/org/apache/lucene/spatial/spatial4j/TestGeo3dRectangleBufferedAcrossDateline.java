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

import org.apache.lucene.spatial3d.geom.PlanetModel;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Point;
import org.locationtech.spatial4j.shape.Rectangle;
import org.locationtech.spatial4j.shape.Shape;
import org.locationtech.spatial4j.shape.SpatialRelation;

/**
 * Rectangle.getBuffered of a Geo3D rectangle next to the dateline does not reach across it, unlike the same
 * rectangle of the plain geo SpatialContext.
 */
public class TestGeo3dRectangleBufferedAcrossDateline extends LuceneTestCase {

  public void testBufferedRectangleReachesAcrossDateline() {
    Geo3dSpatialContextFactory factory = new Geo3dSpatialContextFactory();
    factory.planetModel = PlanetModel.WGS84;
    SpatialContext geo3d = factory.newSpatialContext();
    SpatialContext plain = SpatialContext.GEO;
    for (SpatialContext ctx : new SpatialContext[] {plain, geo3d}) {
      Rectangle r = ctx.getShapeFactory().rect(170, 179, -5, 5);
      Shape buffered = r.getBuffered(5.0, ctx);
      // 2.5 degrees east of the rectangle's east edge, across the dateline
      Point p = ctx.getShapeFactory().pointXY(-178.5, 0);
      assertEquals(
          (ctx == plain ? "plain geo" : "Geo3D") + " context: " + buffered + " must contain " + p,
          SpatialRelation.CONTAINS,
          buffered.relate(p));
    }
  }
}
