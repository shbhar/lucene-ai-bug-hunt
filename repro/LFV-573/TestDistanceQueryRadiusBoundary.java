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

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.ShapeField.QueryRelation;
import org.apache.lucene.geo.Circle;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.SloppyMath;

/**
 * A distance query matches the points "within the specified distance". With the distance that
 * Lucene itself computes for a point (SloppyMath.haversinMeters, also the value a distance sort
 * reports), a query whose radius equals that distance must match the point, and a query whose
 * radius is smaller must not.
 */
public class TestDistanceQueryRadiusBoundary extends LuceneTestCase {

  // all coordinates below are exact encoded grid values, so indexing does not move them
  private static final double CENTER_LAT = -23.073417018167675, CENTER_LON = -155.66040300764143;
  private static final double IN_LAT = 49.503454877994955, IN_LON = -167.46806376613677;
  private static final double OUT_LAT = 56.79421530570835, OUT_LON = 8.682850794866681;

  private static List<Query> distanceQueries(double radius) {
    return List.of(
        LatLonPoint.newDistanceQuery("p", CENTER_LAT, CENTER_LON, radius),
        LatLonDocValuesField.newSlowDistanceQuery("p", CENTER_LAT, CENTER_LON, radius),
        LatLonShape.newDistanceQuery(
            "s", QueryRelation.INTERSECTS, new Circle(CENTER_LAT, CENTER_LON, radius)));
  }

  private static IndexReader index(Directory dir, double lat, double lon) throws Exception {
    try (RandomIndexWriter w = new RandomIndexWriter(random(), dir)) {
      Document doc = new Document();
      doc.add(new LatLonPoint("p", lat, lon));
      doc.add(new LatLonDocValuesField("p", lat, lon));
      for (Field f : LatLonShape.createIndexableFields("s", lat, lon)) {
        doc.add(f);
      }
      w.addDocument(doc);
      return w.getReader();
    }
  }

  /** radius == the point's own distance: inclusive, so the point must match */
  public void testPointAtExactlyTheRadiusMatches() throws Exception {
    try (Directory dir = newDirectory();
        IndexReader reader = index(dir, IN_LAT, IN_LON)) {
      IndexSearcher searcher = newSearcher(reader);
      // the distance a distance sort reports for the document
      TopFieldDocs sorted =
          searcher.search(
              new MatchAllDocsQuery(),
              1,
              new Sort(LatLonDocValuesField.newDistanceSort("p", CENTER_LAT, CENTER_LON)));
      double sortDistance = (Double) ((FieldDoc) sorted.scoreDocs[0]).fields[0];
      double distance = SloppyMath.haversinMeters(CENTER_LAT, CENTER_LON, IN_LAT, IN_LON);
      assertEquals("distance sort and SloppyMath agree", distance, sortDistance, 0.0);
      List<String> wrong = new ArrayList<>();
      for (Query q : distanceQueries(distance)) {
        if (searcher.count(q) != 1) {
          wrong.add(q.getClass().getSimpleName() + " " + q);
        }
      }
      assertTrue(
          "point at distance "
              + distance
              + " m not matched by a query of radius "
              + distance
              + ": "
              + wrong,
          wrong.isEmpty());
    }
  }

  /** radius one ulp smaller than the point's distance: the point is outside and must not match */
  public void testPointBeyondTheRadiusDoesNotMatch() throws Exception {
    try (Directory dir = newDirectory();
        IndexReader reader = index(dir, OUT_LAT, OUT_LON)) {
      IndexSearcher searcher = newSearcher(reader);
      double distance = SloppyMath.haversinMeters(CENTER_LAT, CENTER_LON, OUT_LAT, OUT_LON);
      double radius = Math.nextDown(distance);
      List<String> wrong = new ArrayList<>();
      for (Query q : distanceQueries(radius)) {
        if (searcher.count(q) != 0) {
          wrong.add(q.getClass().getSimpleName() + " " + q);
        }
      }
      assertTrue(
          "point at distance "
              + distance
              + " m matched by a query of radius "
              + radius
              + ": "
              + wrong,
          wrong.isEmpty());
    }
  }
}
