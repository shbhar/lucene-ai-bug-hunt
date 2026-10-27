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
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.spatial3d.geom.GeoPoint;
import org.apache.lucene.spatial3d.geom.PlanetModel;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Geo3DDocValuesField.newDistanceSort reports +Infinity for every document outside maxRadiusMeters,
 * the same value it reports for a document without the field. When the top-N queue is full and its
 * bottom has distance +Infinity, the comparator's bounding-box shortcut in compareBottom rejects a
 * document whose points lie outside the circle without computing its (equal, +Infinity) distance,
 * so the secondary SortField never breaks the tie: search(q, n, sort) does not return the first n
 * hits of the full sorted order.
 */
public class TestGeo3DDistanceSortOutsideRadiusTie extends LuceneTestCase {

  private static List<String> ids(IndexSearcher searcher, TopFieldDocs td) throws Exception {
    List<String> out = new ArrayList<>();
    for (ScoreDoc sd : td.scoreDocs) {
      out.add(searcher.storedFields().document(sd.doc).get("id") + "=" + ((FieldDoc) sd).fields[0]);
    }
    return out;
  }

  /**
   * @param withLocation whether documents 0..4 get a location (all far outside the radius)
   * @param addMissingDoc whether a sixth document without a location is added
   */
  private void check(PlanetModel planetModel, boolean withLocation, boolean addMissingDoc)
      throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (int i = 0; i < 5; i++) {
          Document doc = new Document();
          doc.add(new org.apache.lucene.document.StoredField("id", Integer.toString(i)));
          doc.add(new NumericDocValuesField("tie", i));
          if (withLocation) {
            // latitude 60, longitude 0: about 6,700 km from the origin, far outside the radius
            doc.add(
                new Geo3DDocValuesField(
                    "loc",
                    new GeoPoint(planetModel, Math.toRadians(60), Math.toRadians(0)),
                    planetModel));
          }
          w.addDocument(doc);
        }
        if (addMissingDoc) {
          Document doc = new Document(); // no location: documented to sort as +Infinity
          doc.add(new org.apache.lucene.document.StoredField("id", "5"));
          doc.add(new NumericDocValuesField("tie", 5));
          w.addDocument(doc);
        }
        w.forceMerge(1);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        // 1,000 km on WGS84; on SPHERE distances are in planet units (mean radius 1)
        double maxRadius = planetModel == PlanetModel.SPHERE ? 0.2 : 1_000_000;
        Sort sort =
            new Sort(
                Geo3DDocValuesField.newDistanceSort("loc", 0, 0, maxRadius, planetModel),
                new SortField("tie", SortField.Type.LONG, true)); // ties: highest "tie" first
        List<String> full = ids(searcher, searcher.search(new MatchAllDocsQuery(), 100, sort));
        List<String> top2 = ids(searcher, searcher.search(new MatchAllDocsQuery(), 2, sort));
        // every hit ties on +Infinity, so the full order is by "tie" descending
        assertEquals(
            "full order",
            addMissingDoc
                ? "[5=Infinity, 4=Infinity, 3=Infinity, 2=Infinity, 1=Infinity, 0=Infinity]"
                : "[4=Infinity, 3=Infinity, 2=Infinity, 1=Infinity, 0=Infinity]",
            full.toString());
        assertEquals("top 2 must be the first 2 hits of the full order", full.subList(0, 2), top2);
      }
    }
  }

  /** Fails on main: [1, 0] instead of [4, 3]. */
  public void testOutsideRadiusTieWgs84() throws Exception {
    check(PlanetModel.WGS84, true, false);
  }

  /** Fails on main: [1, 0] instead of [4, 3]. */
  public void testOutsideRadiusTieSphere() throws Exception {
    check(PlanetModel.SPHERE, true, false);
  }

  /**
   * Fails on main: [5, 1] instead of [5, 4]; the document without a location (also +Infinity) is
   * compared on the secondary sort, the documents outside the radius are not.
   */
  public void testOutsideRadiusAndMissingTieWgs84() throws Exception {
    check(PlanetModel.WGS84, true, true);
  }

  /** Control, passes on main: documents without a location tie and the secondary sort decides. */
  public void testMissingOnlyTieControl() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document withLoc = new Document(); // one document inside the radius so the field exists
        withLoc.add(new org.apache.lucene.document.StoredField("id", "9"));
        withLoc.add(new NumericDocValuesField("tie", -1));
        withLoc.add(
            new Geo3DDocValuesField(
                "loc", new GeoPoint(PlanetModel.WGS84, 0, Math.toRadians(1)), PlanetModel.WGS84));
        w.addDocument(withLoc);
        for (int i = 0; i < 5; i++) {
          Document doc = new Document();
          doc.add(new org.apache.lucene.document.StoredField("id", Integer.toString(i)));
          doc.add(new NumericDocValuesField("tie", i));
          w.addDocument(doc);
        }
        w.forceMerge(1);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Sort sort =
            new Sort(
                Geo3DDocValuesField.newDistanceSort("loc", 0, 0, 1_000_000, PlanetModel.WGS84),
                new SortField("tie", SortField.Type.LONG, true));
        List<String> full = ids(searcher, searcher.search(new MatchAllDocsQuery(), 100, sort));
        List<String> top3 = ids(searcher, searcher.search(new MatchAllDocsQuery(), 3, sort));
        assertEquals(full.subList(0, 3), top3);
      }
    }
  }
}
