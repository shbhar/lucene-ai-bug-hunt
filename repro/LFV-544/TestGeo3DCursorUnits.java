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
import org.apache.lucene.document.LatLonDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.Field;
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
import org.apache.lucene.spatial3d.geom.PlanetModel;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Intended-behaviour probe. Questions: (1) is the FieldDoc value really meters, i.e. is the
 * cursor a user passes back in meters? (2) does feeding every returned hit back as the searchAfter
 * cursor give the rest of the sorted list, as for the core LatLon twin? (3) which unit does
 * compareTop actually expect (only a hand-converted cursor in planet units pages correctly)?
 *
 * <p>Index: 8 docs at latitude 3..10 degrees, longitude 0, WGS84 (Geo3DPoint's documented default
 * planet), written in two segments; plus one doc without a location.
 */
public class TestGeo3DCursorUnits extends LuceneTestCase {

  @SuppressWarnings("deprecation")
  private static final Query MATCH_ALL = new MatchAllDocsQuery();

  private static final int N = 8;

  private static List<Integer> docs(ScoreDoc[] hits) {
    List<Integer> out = new ArrayList<>();
    for (ScoreDoc sd : hits) {
      out.add(sd.doc);
    }
    return out;
  }

  private Directory buildIndex(boolean withMissing) throws Exception {
    Directory dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (int i = 0; i < N; i++) {
        Document doc = new Document();
        double lat = 3 + i;
        doc.add(new Geo3DPoint("pt", lat, 0)); // default planet: WGS84
        doc.add(new Geo3DDocValuesField("p3", new org.apache.lucene.spatial3d.geom.GeoPoint(
            PlanetModel.WGS84, Math.toRadians(lat), 0), PlanetModel.WGS84));
        doc.add(new LatLonDocValuesField("ll", lat, 0));
        doc.add(new StringField("id", Integer.toString(i), Field.Store.NO));
        w.addDocument(doc);
        if (i == N / 2) {
          w.commit(); // second segment
        }
      }
      if (withMissing) {
        Document doc = new Document();
        doc.add(new StringField("id", "missing", Field.Store.NO));
        w.addDocument(doc);
      }
    }
    return dir;
  }

  /** Returns a description of every cursor position whose searchAfter page is wrong. */
  private List<String> roundTrip(IndexSearcher searcher, Query q, Sort sort, int total)
      throws Exception {
    TopDocs full = searcher.search(q, total + 5, sort);
    assertEquals(total, full.scoreDocs.length);
    List<Integer> all = docs(full.scoreDocs);
    List<String> bad = new ArrayList<>();
    for (int i = 0; i < full.scoreDocs.length - 1; i++) {
      FieldDoc cursor = (FieldDoc) full.scoreDocs[i];
      TopDocs next = searcher.searchAfter(cursor, q, total + 5, sort);
      List<Integer> expected = all.subList(i + 1, all.size());
      List<Integer> actual = docs(next.scoreDocs);
      if (expected.equals(actual) == false) {
        bad.add("after hit#" + i + " value=" + cursor.fields[0] + " expected=" + expected
            + " actual=" + actual);
      }
    }
    return bad;
  }

  /** (1) The FieldDoc value is meters as documented: 3 degrees of arc on a ~6371 km sphere. */
  public void testFieldDocValueIsMeters() throws Exception {
    try (Directory dir = buildIndex(false);
        DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = new IndexSearcher(r);
      Sort sort = new Sort(Geo3DDocValuesField.newDistanceSort("p3", 0, 0, 5_000_000, PlanetModel.WGS84));
      TopDocs td = s.search(MATCH_ALL, 1, sort);
      double v = (Double) ((FieldDoc) td.scoreDocs[0]).fields[0];
      double approx = Math.toRadians(3) * PlanetModel.WGS84.getMeanRadius();
      assertEquals("FieldDoc value is meters", approx, v, approx * 0.01);
      // and the core twin agrees to within 1%
      Sort llSort = new Sort(LatLonDocValuesField.newDistanceSort("ll", 0, 0));
      double llv = (Double) ((FieldDoc) s.search(MATCH_ALL, 1, llSort).scoreDocs[0]).fields[0];
      assertEquals("LatLon twin value in meters", llv, v, llv * 0.01);
    }
  }

  /** Control: the core LatLon twin pages correctly from every cursor (same index). */
  public void testLatLonTwinRoundTripControl() throws Exception {
    try (Directory dir = buildIndex(true);
        DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = new IndexSearcher(r);
      Sort sort = new Sort(LatLonDocValuesField.newDistanceSort("ll", 0, 0));
      List<String> bad = roundTrip(s, MATCH_ALL, sort, N + 1);
      assertTrue("LatLon twin: " + bad, bad.isEmpty());
    }
  }

  /** (2) Realistic use: distance query on Geo3DPoint (default WGS84) + Geo3D distance sort. */
  public void testGeo3DRoundTripWithDistanceQuery() throws Exception {
    try (Directory dir = buildIndex(false);
        DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = new IndexSearcher(r);
      Query q = Geo3DPoint.newDistanceQuery("pt", PlanetModel.WGS84, 0, 0, 2_000_000);
      Sort sort = new Sort(Geo3DDocValuesField.newDistanceSort("p3", 0, 0, 2_000_000, PlanetModel.WGS84));
      List<String> bad = roundTrip(s, q, sort, N);
      assertTrue("Geo3D distance sort, bad cursors (" + bad.size() + "): " + bad, bad.isEmpty());
    }
  }

  /** (2b) MatchAll with one document missing the field (+Infinity distance, sorts last). */
  public void testGeo3DRoundTripWithMissingDoc() throws Exception {
    try (Directory dir = buildIndex(true);
        DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = new IndexSearcher(r);
      Sort sort = new Sort(Geo3DDocValuesField.newDistanceSort("p3", 0, 0, 2_000_000, PlanetModel.WGS84));
      List<String> bad = roundTrip(s, MATCH_ALL, sort, N + 1);
      assertTrue("Geo3D distance sort + missing doc, bad cursors (" + bad.size() + "): " + bad,
          bad.isEmpty());
    }
  }

  /**
   * (3) Which unit compareTop expects: a cursor hand-converted from meters to planet units
   * (value / meanRadius) pages correctly on stock code. Nothing documents such a conversion; it
   * only shows the defect is a unit mismatch between value() and compareTop.
   */
  public void testHandConvertedCursorPagesCorrectly() throws Exception {
    try (Directory dir = buildIndex(false);
        DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = new IndexSearcher(r);
      Sort sort = new Sort(Geo3DDocValuesField.newDistanceSort("p3", 0, 0, 2_000_000, PlanetModel.WGS84));
      TopDocs full = s.search(MATCH_ALL, N, sort);
      List<Integer> all = docs(full.scoreDocs);
      FieldDoc last = (FieldDoc) full.scoreDocs[1];
      double meters = (Double) last.fields[0];
      FieldDoc converted =
          new FieldDoc(last.doc, last.score, new Object[] {meters / PlanetModel.WGS84.getMeanRadius()});
      TopDocs page2 = s.searchAfter(converted, MATCH_ALL, N, sort);
      assertEquals(all.subList(2, N), docs(page2.scoreDocs));
    }
  }
}
