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

import org.apache.lucene.document.ShapeField.QueryRelation;
import org.apache.lucene.geo.XYLine;
import org.junit.Assert;
import org.junit.Test;

/**
 * Version-neutral check (plain JUnit 4, compiles against 8.2 through main): a cartesian WITHIN
 * query with a line geometry must be rejected, as it was before the XY query classes were merged
 * into one query and as the LatLonShape twin still does.
 */
public class TestXYShapeWithinLineRejected {

  @Test
  public void testWithinLineQueryIsRejected() {
    XYLine line = new XYLine(new float[] {0, 10}, new float[] {0, 0});
    try {
      XYShape.newLineQuery("shape", QueryRelation.WITHIN, line);
    } catch (IllegalArgumentException expected) {
      System.out.println("REJECTED: " + expected.getMessage());
      return;
    }
    System.out.println("ACCEPTED: no exception");
    Assert.fail("XYShape.newLineQuery(field, WITHIN, line) was accepted; expected IllegalArgumentException");
  }
}
