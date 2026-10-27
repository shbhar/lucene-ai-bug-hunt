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

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.geo.Polygon;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.spatial3d.geom.GeoPoint;
import org.apache.lucene.spatial3d.geom.PlanetModel;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Paging with IndexSearcher.searchAfter through a Geo3D sort must return the rest of the full
 * sorted result. Six documents lie 3..8 degrees north of the origin, one point each. Page 1 holds
 * the first two hits of the sort; page 2 (searchAfter the second hit) must hold the other four.
 */
public class TestGeo3DSortSearchAfter extends LuceneTestCase {

  // the constructor (deprecated on main in favour of INSTANCE) also compiles against 9.x releases
  @SuppressWarnings("deprecation")
  private static final Query MATCH_ALL = new MatchAllDocsQuery();

  private static final int N = 6;

  // a square of +/- 1 degree around the origin; every document lies outside it
  private static final Polygon SQUARE =
      new Polygon(new double[] {-1, 1, 1, -1, -1}, new double[] {-1, -1, 1, 1, -1});

  private static List<Integer> docs(ScoreDoc[] hits) {
    List<Integer> out = new ArrayList<>();
    for (ScoreDoc sd : hits) {
      out.add(sd.doc);
    }
    return out;
  }

  private void checkPaging(PlanetModel planet, SortField sortField) throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (int i = 0; i < N; i++) {
          Document doc = new Document();
          doc.add(
              new Geo3DDocValuesField("p", new GeoPoint(planet, Math.toRadians(3 + i), 0), planet));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Sort sort = new Sort(sortField);
        TopDocs full = searcher.search(MATCH_ALL, N, sort);
        assertEquals(N, full.scoreDocs.length);
        // non-vacuity: every hit has a finite distance and the distances are strictly increasing
        double prev = Double.NEGATIVE_INFINITY;
        for (ScoreDoc sd : full.scoreDocs) {
          double d = (Double) ((FieldDoc) sd).fields[0];
          assertTrue(sortField + ": finite increasing distances", Double.isFinite(d) && d > prev);
          prev = d;
        }
        TopDocs page1 = searcher.search(MATCH_ALL, 2, sort);
        assertEquals(docs(full.scoreDocs).subList(0, 2), docs(page1.scoreDocs));
        FieldDoc last = (FieldDoc) page1.scoreDocs[1];
        TopDocs page2 = searcher.searchAfter(last, MATCH_ALL, N, sort);
        assertEquals(
            sortField + ": searchAfter(" + last.fields[0] + ") must return the remaining hits",
            docs(full.scoreDocs).subList(2, N),
            docs(page2.scoreDocs));
      }
    }
  }

  public void testDistanceSortWgs84() throws Exception {
    checkPaging(
        PlanetModel.WGS84,
        Geo3DDocValuesField.newDistanceSort("p", 0, 0, 2_000_000, PlanetModel.WGS84));
  }

  public void testDistanceSortClarke1866() throws Exception {
    checkPaging(
        PlanetModel.CLARKE_1866,
        Geo3DDocValuesField.newDistanceSort("p", 0, 0, 2_000_000, PlanetModel.CLARKE_1866));
  }

  public void testPathSortWgs84() throws Exception {
    checkPaging(
        PlanetModel.WGS84,
        Geo3DDocValuesField.newPathSort(
            "p", new double[] {0, 10}, new double[] {-1, -1}, 500_000, PlanetModel.WGS84));
  }

  public void testOutsideDistanceSortWgs84() throws Exception {
    // a 200 km circle around the origin: every document lies outside it
    checkPaging(
        PlanetModel.WGS84,
        Geo3DDocValuesField.newOutsideDistanceSort("p", 0, 0, 200_000, PlanetModel.WGS84));
  }

  public void testOutsideBoxSortWgs84() throws Exception {
    checkPaging(
        PlanetModel.WGS84,
        Geo3DDocValuesField.newOutsideBoxSort("p", -1, 1, -1, 1, PlanetModel.WGS84));
  }

  public void testOutsidePolygonSortWgs84() throws Exception {
    checkPaging(
        PlanetModel.WGS84,
        Geo3DDocValuesField.newOutsidePolygonSort("p", PlanetModel.WGS84, SQUARE));
  }

  public void testOutsideLargePolygonSortWgs84() throws Exception {
    checkPaging(
        PlanetModel.WGS84,
        Geo3DDocValuesField.newOutsideLargePolygonSort("p", PlanetModel.WGS84, SQUARE));
  }

  public void testOutsidePathSortWgs84() throws Exception {
    // a 100 km wide path along longitude -5: every document lies outside it
    checkPaging(
        PlanetModel.WGS84,
        Geo3DDocValuesField.newOutsidePathSort(
            "p", new double[] {0, 10}, new double[] {-5, -5}, 100_000, PlanetModel.WGS84));
  }

  /**
   * Control: on the unit sphere (mean radius 1.0) meters and planet units are the same number, and
   * the same paging is correct.
   */
  public void testControlDistanceSortUnitSphere() throws Exception {
    checkPaging(
        PlanetModel.SPHERE,
        Geo3DDocValuesField.newDistanceSort("p", 0, 0, 0.5, PlanetModel.SPHERE));
  }
}
