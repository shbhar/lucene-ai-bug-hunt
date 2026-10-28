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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.spatial.query.SpatialArgsParser;
import org.apache.lucene.spatial.serialized.SerializedDVStrategy;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;

/**
 * A query "Intersects(BUFFER(ENVELOPE(172, 178, 4, -4), 4))" covers longitudes 168 to -178 (182).
 * One document is a point at (-179.5, 1), 0.5 degrees past the dateline and inside the buffer.
 * SerializedDVStrategy compares exact shapes, so no grid approximation is involved.
 */
public class TestGeo3dBufferQueryAcrossDateline extends LuceneTestCase {

  private int countHits(SpatialContext ctx, double x, double y, String query) throws Exception {
    SerializedDVStrategy strategy = new SerializedDVStrategy(ctx, "geo");
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        Document doc = new Document();
        for (Field f : strategy.createIndexableFields(ctx.getShapeFactory().pointXY(x, y))) {
          doc.add(f);
        }
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        return s.count(strategy.makeQuery(new SpatialArgsParser().parse(query, ctx)));
      }
    }
  }

  private static SpatialContext geo3d() {
    return new Geo3dSpatialContextFactory().newSpatialContext();
  }

  /** Control (passes): the plain spatial4j geo context finds the point. */
  public void testControlPlainGeoContext() throws Exception {
    assertEquals(
        1,
        countHits(
            SpatialContext.GEO, -179.5, 1, "Intersects(BUFFER(ENVELOPE(172, 178, 4, -4), 4))"));
  }

  /** Control (passes): Geo3D context, the same geometry 20 degrees west of the dateline. */
  public void testControlGeo3dAwayFromDateline() throws Exception {
    assertEquals(
        1, countHits(geo3d(), 160.5, 1, "Intersects(BUFFER(ENVELOPE(152, 158, 4, -4), 4))"));
  }

  /** Control (passes): Geo3D context, the widened envelope written with wrapped bounds. */
  public void testControlGeo3dWrappedEnvelope() throws Exception {
    assertEquals(1, countHits(geo3d(), -179.5, 1, "Intersects(ENVELOPE(168, -178, 8, -8))"));
  }

  /** Geo3D context next to the dateline: the buffer stops at 180 and the point is missed. */
  public void testGeo3dBufferReachesAcrossDateline() throws Exception {
    assertEquals(
        1, countHits(geo3d(), -179.5, 1, "Intersects(BUFFER(ENVELOPE(172, 178, 4, -4), 4))"));
  }
}
