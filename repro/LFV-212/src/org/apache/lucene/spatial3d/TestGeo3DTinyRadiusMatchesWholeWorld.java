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
package org.apache.lucene.spatial3d;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.spatial3d.geom.PlanetModel;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Geo3DPoint.newDistanceQuery with a 1.24 cm radius matches documents on the other side of the
 * planet. Tiny circles are either rejected with IllegalArgumentException ("Couldn't construct
 * circle plane, probably too small?") or, as here, built with the cut plane's inside on the wrong
 * side, so the "circle" is the whole world minus a speck.
 */
public class TestGeo3DTinyRadiusMatchesWholeWorld extends LuceneTestCase {

  public void testTinyRadiusDoesNotMatchFarPoints() throws Exception {
    final PlanetModel planetModel = PlanetModel.WGS84;
    final double lat = -6.106045660234088, lon = -17.47654698237369;
    final double radiusMeters = 0.012391875163764922;
    final Query q;
    try {
      q = Geo3DPoint.newDistanceQuery("point", planetModel, lat, lon, radiusMeters);
    } catch (IllegalArgumentException e) {
      return; // rejecting a circle that is too small to represent is acceptable
    }
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
      // three documents, each thousands of kilometres from the centre
      double[][] far = {{-lat, lon + 180}, {lat + 45, lon}, {lat, lon + 90}};
      for (double[] ll : far) {
        Document doc = new Document();
        doc.add(new Geo3DPoint("point", planetModel, ll[0], ll[1]));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = newSearcher(reader);
        assertEquals(
            "a " + radiusMeters + " m distance query must not match points thousands of km away",
            0,
            searcher.count(q));
      }
    }
  }
}
