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
import java.util.Locale;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.spatial3d.geom.GeoPoint;
import org.apache.lucene.spatial3d.geom.PlanetModel;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * Geo3DDocValuesField sorts on documents with several points. The javadoc allows several values per
 * document and says that the closest one is used. Every document lies due north of the origin; each
 * inner array lists one document's latitudes in degrees. All documents go into one segment of the
 * default codec, in this order, so doc ids are the array indexes.
 */
public class TestGeo3DSortMultiValued extends LuceneTestCase {

  /** Doc 2 has two points; its closest is 0.5 degrees, so the full order is [2, 0, 1, 3, 4]. */
  private static final double[][] MULTI_IN_MIDDLE = {{1}, {2}, {9, 0.5}, {5}, {6}};

  /** Doc 2 has one point, but doc 4 has two, so the segment stores the field as multi-valued. */
  private static final double[][] MULTI_ELSEWHERE = {{1}, {2}, {0.5}, {5}, {6, 7}};

  /** The document with two points is the last document. */
  private static final double[][] MULTI_LAST = {{1}, {2}, {9, 0.5}};

  /** Control: every document has one point. */
  private static final double[][] ALL_SINGLE = {{1}, {2}, {0.5}, {5}, {6}};

  /** On the unit sphere a distance "in meters" is the arc in radians. */
  private static final double DELTA = 1e-5;

  private static SortField sphereDistanceSort() {
    return Geo3DDocValuesField.newDistanceSort("p", 0, 0, 0.5, PlanetModel.SPHERE);
  }

  public void testTopTwoWithMultiValuedDocument() throws Exception {
    assertTopTwo(MULTI_IN_MIDDLE, sphereDistanceSort(), 0.5, 1);
  }

  public void testTopTwoSingleValuedHitInMultiValuedSegment() throws Exception {
    assertTopTwo(MULTI_ELSEWHERE, sphereDistanceSort(), 0.5, 1);
  }

  public void testTopTwoWhenMultiValuedDocumentIsLast() throws Exception {
    assertTopTwo(MULTI_LAST, sphereDistanceSort(), 0.5, 1);
  }

  public void testOutsideDistanceSort() throws Exception {
    // every point lies outside a circle of 0.1 degree, so the distances are 0.4 and 0.9 degrees
    SortField sort =
        Geo3DDocValuesField.newOutsideDistanceSort(
            "p", 0, 0, Math.toRadians(0.1), PlanetModel.SPHERE);
    assertTopTwo(MULTI_IN_MIDDLE, sort, 0.4, 0.9);
  }

  public void testTopTwoWgs84() throws Exception {
    SortField sortField =
        Geo3DDocValuesField.newDistanceSort("p", 0, 0, 2_000_000, PlanetModel.WGS84);
    try (Directory dir = index(MULTI_IN_MIDDLE, PlanetModel.WGS84);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      Sort sort = new Sort(sortField);
      TopDocs all = searcher.search(MatchAllDocsQuery.INSTANCE, 5, sort);
      assertEquals(List.of(2, 0, 1, 3, 4), docs(all));
      TopDocs top2 = searcher.search(MatchAllDocsQuery.INSTANCE, 2, sort);
      // the top 2 must be the head of the full order, with the same sort values (in meters)
      String message = "top 2 = " + render(top2, PlanetModel.WGS84);
      assertEquals(message, List.of(2, 0), docs(top2));
      assertEquals(message, value(all, 0), value(top2, 0), 0.0);
      assertEquals(message, value(all, 1), value(top2, 1), 0.0);
    }
  }

  public void testSearchAfterWithMultiValuedDocument() throws Exception {
    assertSearchAfter(MULTI_IN_MIDDLE);
  }

  /** Control: passes with and without the bug. */
  public void testAllSingleValuedSegment() throws Exception {
    assertTopTwo(ALL_SINGLE, sphereDistanceSort(), 0.5, 1);
    assertSearchAfter(ALL_SINGLE);
  }

  /** After doc 2 (0.5 degrees) the next page of 2 is doc 0 (1 degree) and doc 1 (2 degrees). */
  private void assertSearchAfter(double[][] lats) throws Exception {
    try (Directory dir = index(lats, PlanetModel.SPHERE);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      Sort sort = new Sort(sphereDistanceSort());
      // asking for every hit reads each document once, so this first page is right
      TopDocs all = searcher.search(MatchAllDocsQuery.INSTANCE, lats.length, sort);
      assertEquals(2, all.scoreDocs[0].doc);
      FieldDoc after = (FieldDoc) all.scoreDocs[0];
      TopDocs next = searcher.searchAfter(after, MatchAllDocsQuery.INSTANCE, 2, sort);
      String message = "page after doc 2 = " + render(next, PlanetModel.SPHERE);
      assertEquals(message, List.of(0, 1), docs(next));
      assertEquals(message, Math.toRadians(1), value(next, 0), DELTA);
      assertEquals(message, Math.toRadians(2), value(next, 1), DELTA);
    }
  }

  /** The top 2 hits must be doc 2 then doc 0, each with its own distance in degrees. */
  private void assertTopTwo(double[][] lats, SortField sortField, double deg2, double deg0)
      throws Exception {
    try (Directory dir = index(lats, PlanetModel.SPHERE);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      TopDocs top2 = searcher.search(MatchAllDocsQuery.INSTANCE, 2, new Sort(sortField));
      String message = "top 2 = " + render(top2, PlanetModel.SPHERE);
      assertEquals(message, List.of(2, 0), docs(top2));
      assertEquals(message, Math.toRadians(deg2), value(top2, 0), DELTA);
      assertEquals(message, Math.toRadians(deg0), value(top2, 1), DELTA);
    }
  }

  private static Directory index(double[][] lats, PlanetModel planet) throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (double[] docLats : lats) {
        Document doc = new Document();
        for (double lat : docLats) {
          GeoPoint point = new GeoPoint(planet, Math.toRadians(lat), 0);
          doc.add(new Geo3DDocValuesField("p", point, planet));
        }
        w.addDocument(doc);
      }
    }
    try (DirectoryReader reader = DirectoryReader.open(dir)) {
      assertEquals("one segment", 1, reader.leaves().size());
    }
    return dir;
  }

  private static List<Integer> docs(TopDocs topDocs) {
    List<Integer> out = new ArrayList<>();
    for (ScoreDoc sd : topDocs.scoreDocs) {
      out.add(sd.doc);
    }
    return out;
  }

  private static double value(TopDocs topDocs, int i) {
    return (Double) ((FieldDoc) topDocs.scoreDocs[i]).fields[0];
  }

  /** The hits as "doc @ sort value as degrees of arc", for the assertion messages. */
  private static String render(TopDocs topDocs, PlanetModel planet) {
    List<String> out = new ArrayList<>();
    for (int i = 0; i < topDocs.scoreDocs.length; i++) {
      double degrees = Math.toDegrees(value(topDocs, i) / planet.getMeanRadius());
      out.add(String.format(Locale.ROOT, "%d @ %.2f deg", topDocs.scoreDocs[i].doc, degrees));
    }
    return out.toString();
  }
}
