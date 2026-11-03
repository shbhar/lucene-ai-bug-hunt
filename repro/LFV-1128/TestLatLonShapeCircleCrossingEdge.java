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
import org.apache.lucene.geo.Circle;
import org.apache.lucene.geo.Line;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.SloppyMath;

public class TestLatLonShapeCircleCrossingEdge extends LuceneTestCase {

  public void testLineThroughCircleWithBothEndsOutside() throws Exception {
    Circle circle = new Circle(60, 10, 10_000);
    Line line = new Line(new double[] {60.38, 59.78}, new double[] {9.8, 10.4});
    // (60.04, 10.14) is on the line and inside the circle: expected 1 INTERSECTS hit, actual 0.
    assertTrue(SloppyMath.haversinMeters(60, 10, 60.04, 10.14) < 10_000);

    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        for (Field f : LatLonShape.createIndexableFields("shape", line)) {
          doc.add(f);
        }
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(
            1,
            new IndexSearcher(r)
                .count(LatLonShape.newDistanceQuery("shape", QueryRelation.INTERSECTS, circle)));
      }
    }
  }
}
