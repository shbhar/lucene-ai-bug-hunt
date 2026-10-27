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
import org.apache.lucene.geo.Rectangle;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A box whose minimum latitude is 90 (it touches only the north pole), or whose longitude range is
 * exactly [180, 180], is turned into "match nothing" by LatLonPoint.newBoxQuery, but not by the
 * other public paths that evaluate the same box over the same point, so they disagree.
 */
public class TestBoxAtPoleSpecialCase extends LuceneTestCase {

  private static IndexSearcher searcher(IndexReader reader) {
    return newSearcher(reader);
  }

  private static void addPoint(RandomIndexWriter w, double lat, double lon) throws Exception {
    Document doc = new Document();
    doc.add(new LatLonPoint("p", lat, lon));
    doc.add(new LatLonDocValuesField("p", lat, lon));
    for (Field f : LatLonShape.createIndexableFields("s", lat, lon)) {
      doc.add(f);
    }
    w.addDocument(doc);
  }

  private void check(double lat, double lon, Rectangle box) throws Exception {
    try (Directory dir = newDirectory();
        RandomIndexWriter w = new RandomIndexWriter(random(), dir)) {
      addPoint(w, lat, lon);
      try (IndexReader reader = w.getReader()) {
        IndexSearcher s = searcher(reader);
        int intersects = s.count(LatLonPoint.newGeometryQuery("p", QueryRelation.INTERSECTS, box));
        int disjoint = s.count(LatLonPoint.newGeometryQuery("p", QueryRelation.DISJOINT, box));
        int within = s.count(LatLonPoint.newGeometryQuery("p", QueryRelation.WITHIN, box));
        int twoBoxes = s.count(LatLonPoint.newGeometryQuery("p", QueryRelation.INTERSECTS, box, box));
        int dvBox = s.count(LatLonDocValuesField.newSlowBoxQuery("p", box.minLat, box.maxLat, box.minLon, box.maxLon));
        int dvWithin = s.count(LatLonDocValuesField.newSlowGeometryQuery("p", QueryRelation.WITHIN, box));
        int shapeBox =
            s.count(LatLonShape.newBoxQuery("s", QueryRelation.INTERSECTS, box.minLat, box.maxLat, box.minLon, box.maxLon));
        String all =
            String.format(
                java.util.Locale.ROOT,
                "box=%s point=(%s,%s): LatLonPoint INTERSECTS=%d DISJOINT=%d WITHIN=%d INTERSECTS(box,box)=%d;"
                    + " LatLonDocValuesField newSlowBoxQuery=%d WITHIN=%d; LatLonShape INTERSECTS=%d",
                box, lat, lon, intersects, disjoint, within, twoBoxes, dvBox, dvWithin, shapeBox);
        // one point, one box: it either intersects the box or is disjoint from it
        assertEquals("INTERSECTS + DISJOINT must cover the document; " + all, 1, intersects + disjoint);
        // for a single point WITHIN and INTERSECTS are the same predicate
        assertEquals("WITHIN vs INTERSECTS; " + all, within, intersects);
        // the same box given twice is the same region
        assertEquals("INTERSECTS(box) vs INTERSECTS(box, box); " + all, twoBoxes, intersects);
        // points index, doc values and shape index evaluate the same box over the same point
        assertEquals("doc values box vs doc values WITHIN; " + all, dvWithin, dvBox);
        assertEquals("LatLonPoint vs LatLonShape; " + all, shapeBox, intersects);
      }
    }
  }

  /**
   * The equivalence upstream asserts in BaseLatLonPointTestCase#testBoundingBoxQueriesEquivalence
   * (newBoxQuery vs LatLonPointQuery INTERSECTS on the same box), for a box touching only the pole.
   */
  public void testBoxQueriesEquivalenceAtPole() throws Exception {
    try (Directory dir = newDirectory();
        RandomIndexWriter w = new RandomIndexWriter(random(), dir)) {
      addPoint(w, 90.0, 0.0);
      try (IndexReader reader = w.getReader()) {
        IndexSearcher s = searcher(reader);
        Rectangle box = new Rectangle(90.0, 90.0, -180.0, 180.0);
        Query q1 = LatLonPoint.newBoxQuery("p", box.minLat, box.maxLat, box.minLon, box.maxLon);
        Query q2 = new LatLonPointQuery("p", QueryRelation.INTERSECTS, box);
        assertEquals(s.count(q1), s.count(q2));
      }
    }
  }

  /** a point indexed at the north pole and a box that touches only the pole */
  public void testNorthPoleBox() throws Exception {
    check(90.0, 0.0, new Rectangle(90.0, 90.0, -180.0, 180.0));
  }

  /** a point indexed on the antimeridian and a box whose longitude range is [180, 180] */
  public void testAntimeridianBox() throws Exception {
    check(0.0, 180.0, new Rectangle(-10.0, 10.0, 180.0, 180.0));
  }
}
