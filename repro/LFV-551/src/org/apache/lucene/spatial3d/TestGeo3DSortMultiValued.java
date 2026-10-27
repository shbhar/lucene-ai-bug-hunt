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
 * Geo3DDocValuesField.newDistanceSort: "If a document contains multiple values for the field, the
 * closest distance from the circle center is used." Five documents north of the origin, one
 * segment, at latitudes (degrees) {1}, {2}, {9, 0.5}, {5}, {6}. The two nearest are document 2 (0.5
 * degrees) and document 0 (1 degree), whether we ask for the top 2 or for all 5 hits. A second
 * layout puts the multi-valued document last: {1}, {2}, {9, 0.5}.
 */
public class TestGeo3DSortMultiValued extends LuceneTestCase {

  // the constructor (deprecated on main in favour of INSTANCE) also compiles against 9.x releases
  @SuppressWarnings("deprecation")
  private static final Query MATCH_ALL = new MatchAllDocsQuery();

  private static final double[][] LATS = {{1}, {2}, {9, 0.5}, {5}, {6}};

  /**
   * Only the last document is multi-valued, but that makes the segment store the field as
   * multi-valued, so the re-read also hits the single-valued document 2 (0.5 degrees).
   */
  private static final double[][] LATS_SINGLE_IN_MIXED = {{1}, {2}, {0.5}, {5}, {6, 7}};

  /** The multi-valued document is the last one: the re-read runs past the end of the values. */
  private static final double[][] LATS_LAST = {{1}, {2}, {9, 0.5}};

  private static List<Integer> docs(ScoreDoc[] hits) {
    List<Integer> out = new ArrayList<>();
    for (ScoreDoc sd : hits) {
      out.add(sd.doc);
    }
    return out;
  }

  private void check(PlanetModel planet, SortField sortField) throws Exception {
    check(planet, sortField, LATS, List.of(2, 0, 1, 3, 4));
  }

  private void check(PlanetModel planet, SortField sortField, double[][] lats, List<Integer> order)
      throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (double[] docLats : lats) {
          Document doc = new Document();
          for (double lat : docLats) {
            doc.add(
                new Geo3DDocValuesField("p", new GeoPoint(planet, Math.toRadians(lat), 0), planet));
          }
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Sort sort = new Sort(sortField);
        TopDocs all = searcher.search(MATCH_ALL, lats.length, sort);
        assertEquals(sortField + ": all hits", order, docs(all.scoreDocs));
        TopDocs top2 = searcher.search(MATCH_ALL, 2, sort);
        assertEquals(
            sortField + ": top 2 must be the head of the full order, values "
                + ((FieldDoc) top2.scoreDocs[0]).fields[0] + ", "
                + ((FieldDoc) top2.scoreDocs[1]).fields[0],
            order.subList(0, 2),
            docs(top2.scoreDocs));
      }
    }
  }

  public void testDistanceSortUnitSphere() throws Exception {
    check(
        PlanetModel.SPHERE, Geo3DDocValuesField.newDistanceSort("p", 0, 0, 0.5, PlanetModel.SPHERE));
  }

  public void testDistanceSortWgs84() throws Exception {
    check(
        PlanetModel.WGS84,
        Geo3DDocValuesField.newDistanceSort("p", 0, 0, 2_000_000, PlanetModel.WGS84));
  }

  public void testLastDocumentMultiValuedUnitSphere() throws Exception {
    check(
        PlanetModel.SPHERE,
        Geo3DDocValuesField.newDistanceSort("p", 0, 0, 0.5, PlanetModel.SPHERE),
        LATS_LAST,
        List.of(2, 0, 1));
  }

  public void testSingleValuedDocumentInMixedSegmentUnitSphere() throws Exception {
    check(
        PlanetModel.SPHERE,
        Geo3DDocValuesField.newDistanceSort("p", 0, 0, 0.5, PlanetModel.SPHERE),
        LATS_SINGLE_IN_MIXED,
        List.of(2, 0, 1, 3, 4));
  }

  public void testOutsideDistanceSortUnitSphere() throws Exception {
    // a circle of 0.1 degree around the origin: every value lies outside it
    check(
        PlanetModel.SPHERE,
        Geo3DDocValuesField.newOutsideDistanceSort(
            "p", 0, 0, Math.toRadians(0.1), PlanetModel.SPHERE));
  }
}
