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

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.SloppyMath;

/** Same check as TestDistanceQueryRadiusBoundary, with the pre-9.1 test-framework packages. */
public class TestDistanceQueryRadiusBoundaryOld extends LuceneTestCase {
  private static final double CENTER_LAT = -23.073417018167675, CENTER_LON = -155.66040300764143;
  private static final double IN_LAT = 49.503454877994955, IN_LON = -167.46806376613677;
  private static final double OUT_LAT = 56.79421530570835, OUT_LON = 8.682850794866681;

  private int count(double lat, double lon, double radius) throws Exception {
    Directory dir = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), dir);
    Document doc = new Document();
    doc.add(new LatLonPoint("p", lat, lon));
    w.addDocument(doc);
    IndexReader reader = w.getReader();
    w.close();
    IndexSearcher searcher = newSearcher(reader);
    int n = searcher.count(LatLonPoint.newDistanceQuery("p", CENTER_LAT, CENTER_LON, radius));
    reader.close();
    dir.close();
    return n;
  }

  public void testPointAtExactlyTheRadiusMatches() throws Exception {
    double distance = SloppyMath.haversinMeters(CENTER_LAT, CENTER_LON, IN_LAT, IN_LON);
    assertEquals("radius " + distance, 1, count(IN_LAT, IN_LON, distance));
  }

  public void testPointBeyondTheRadiusDoesNotMatch() throws Exception {
    double distance = SloppyMath.haversinMeters(CENTER_LAT, CENTER_LON, OUT_LAT, OUT_LON);
    assertEquals("radius " + Math.nextDown(distance), 0, count(OUT_LAT, OUT_LON, Math.nextDown(distance)));
  }
}
