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
import org.apache.lucene.geo.Polygon;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.spatial3d.geom.PlanetModel;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestGeo3DDegeneratePolygonQuery extends LuceneTestCase {

  public void testDegeneratePolygonMatchesNothing() throws Exception {
    try (ByteBuffersDirectory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      doc.add(new Geo3DPoint("point", PlanetModel.WGS84, 0.5, 0.5));
      w.addDocument(doc);
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        // vertices on the equator, built as an empty GeoCompositePolygon: expected 0 hits, actual NPE
        Polygon degenerate = new Polygon(new double[] {0, 0, 0, 0}, new double[] {0, 1, 2, 0});
        Query q = Geo3DPoint.newPolygonQuery("point", PlanetModel.WGS84, degenerate);
        assertEquals(0, new IndexSearcher(reader).count(q));
      }
    }
  }
}
