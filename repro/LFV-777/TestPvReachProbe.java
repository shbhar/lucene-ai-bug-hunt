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
package org.apache.lucene.spatial.vector;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.DoubleValues;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.context.SpatialContextFactory;
import org.locationtech.spatial4j.shape.Point;

/** V3 reach probe: observes current behaviour (asserts what the stock jar does today). */
public class TestPvReachProbe extends LuceneTestCase {

  private static String order(IndexSearcher s, DirectoryReader r, SortField sf) throws IOException {
    TopDocs top = s.search(new MatchAllDocsQuery(), 2, new Sort(sf));
    return s.storedFields().document(top.scoreDocs[0].doc).get("id")
        + ","
        + s.storedFields().document(top.scoreDocs[1].doc).get("id");
  }

  private static void probe(SpatialContext ctx, double fx, double fy, String label) throws IOException {
    PointVectorStrategy strategy = PointVectorStrategy.newInstance(ctx, "pv");
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        Document far = new Document();
        far.add(new StringField("id", "far", Field.Store.YES));
        for (Field f : strategy.createIndexableFields(ctx.getShapeFactory().pointXY(fx, fy))) {
          far.add(f);
        }
        w.addDocument(far);
        Document none = new Document();
        none.add(new StringField("id", "none", Field.Store.YES));
        w.addDocument(none);
        w.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Point origin = ctx.getShapeFactory().pointXY(0, 0);
        DoubleValuesSource d = strategy.makeDistanceValueSource(origin, 1.0);
        DoubleValues v = d.getValues(reader.leaves().get(0), null);
        assertTrue(v.advanceExact(0));
        double farV = v.doubleValue();
        boolean noneAdv = v.advanceExact(1);
        double noneV = v.doubleValue();
        String asc = order(searcher, reader, d.getSortField(false));
        // user-supplied missing value on the sort field (does it override?)
        String ascMissingMax = order(searcher, reader, d.getSortField(false, Double.MAX_VALUE));
        DoubleValuesSource recip =
            strategy.makeRecipDistanceValueSource(ctx.getShapeFactory().pointXY(0, 0)
                .getBuffered(10, ctx));
        DoubleValues rv = recip.getValues(reader.leaves().get(0), null);
        rv.advanceExact(0);
        double rFar = rv.doubleValue();
        rv.advanceExact(1);
        double rNone = rv.doubleValue();
        String descRecip = order(searcher, reader, recip.getSortField(true));
        System.out.println(
            "V3PROBE "
                + label
                + " geo="
                + ctx.isGeo()
                + " worldBounds="
                + ctx.getWorldBounds()
                + " far=("
                + fx
                + ","
                + fy
                + ") dist(far)="
                + farV
                + " advanceExact(none)="
                + noneAdv
                + " dist(none)="
                + noneV
                + " ascSort="
                + asc
                + " ascSort(missingValue=MAX)="
                + ascMissingMax
                + " recip(far)="
                + rFar
                + " recip(none)="
                + rNone
                + " descRecipSort="
                + descRecip);
        if (!ctx.isGeo()) {
          assertEquals(180.0, noneV, 0.0);
          assertEquals("none,far", asc);
          assertEquals("none,far", ascMissingMax);
          assertTrue(rNone > rFar);
          assertEquals("none,far", descRecip);
        } else {
          assertEquals(180.0, noneV, 0.0);
          assertEquals("far,none", asc);
        }
      }
    }
  }

  /** non-geo context with spatial4j's DEFAULT world bounds (no worldBounds configured). */
  public void testDefaultNonGeoBounds() throws IOException {
    SpatialContextFactory f = new SpatialContextFactory();
    f.geo = false;
    probe(f.newSpatialContext(), 500, 0, "defaultNonGeo");
  }

  /** non-geo context bounded like the geo world (-180..180, -90..90): diagonal points exceed 180. */
  public void testNonGeoGeoSizedWorld() throws IOException {
    SpatialContextFactory f = new SpatialContextFactory();
    f.geo = false;
    f.worldBounds = new org.locationtech.spatial4j.shape.impl.RectangleImpl(-180, 180, -90, 90, null);
    probe(f.newSpatialContext(), 170, 80, "nonGeoGeoSized");
  }

  /** geo context: 180 is the largest possible distance, so missing goes last (unaffected). */
  public void testGeo() throws IOException {
    probe(SpatialContext.GEO, 179, 0, "geo");
  }
}
