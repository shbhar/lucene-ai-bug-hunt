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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.lucene.document.ShapeField.QueryRelation;
import org.apache.lucene.geo.GeoEncodingUtils;
import org.apache.lucene.geo.Point;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexOrDocValuesQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * CONTAINS with several Point geometries asks for the documents whose points include every query
 * point. {@link LatLonPoint#newGeometryQuery} builds that conjunction (one CONTAINS clause per
 * point). {@link LatLonDocValuesField#newSlowGeometryQuery}, which its javadoc pairs with it in an
 * {@link IndexOrDocValuesQuery} ("Both provided queries must match the same documents"), must match
 * the same documents.
 */
public class TestLatLonDocValuesContainsMultiPoint extends LuceneTestCase {

  private static final String FIELD = "loc";

  // points on the encoding grid, so indexed and query points are equal after encoding
  private static final Point P1 = gridPoint(22.5, 45);
  private static final Point P2 = gridPoint(45, 135);
  private static final Point P3 = gridPoint(-22.5, -90);

  private static Point gridPoint(double lat, double lon) {
    return new Point(
        GeoEncodingUtils.decodeLatitude(GeoEncodingUtils.encodeLatitude(lat)),
        GeoEncodingUtils.decodeLongitude(GeoEncodingUtils.encodeLongitude(lon)));
  }

  private static Document doc(String id, Point... points) {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.YES));
    for (Point p : points) {
      doc.add(new LatLonPoint(FIELD, p.getLat(), p.getLon()));
      doc.add(new LatLonDocValuesField(FIELD, p.getLat(), p.getLon()));
    }
    return doc;
  }

  /** A = {P1}, B = {P2}, C = {P1, P2}, D = {P3}: only C holds both P1 and P2. */
  private static void addExample(IndexWriter w) throws IOException {
    w.addDocument(doc("A", P1));
    w.addDocument(doc("B", P2));
    w.addDocument(doc("C", P1, P2));
    w.addDocument(doc("D", P3));
  }

  private static IndexSearcher searcher(IndexReader reader) {
    assertEquals(1, reader.leaves().size());
    IndexSearcher searcher = new IndexSearcher(reader);
    // no query cache: a cached entry would always be computed from the points side
    searcher.setQueryCache(null);
    return searcher;
  }

  /** the sorted ids of all hits */
  private static List<String> ids(IndexSearcher searcher, Query query) throws IOException {
    StoredFields storedFields = searcher.storedFields();
    List<String> ids = new ArrayList<>();
    for (ScoreDoc hit : searcher.search(query, 10_000).scoreDocs) {
      ids.add(storedFields.document(hit.doc).get("id"));
    }
    Collections.sort(ids);
    return ids;
  }

  private static Query points(Point... query) {
    return LatLonPoint.newGeometryQuery(FIELD, QueryRelation.CONTAINS, query);
  }

  private static Query docValues(Point... query) {
    return LatLonDocValuesField.newSlowGeometryQuery(FIELD, QueryRelation.CONTAINS, query);
  }

  public void testDocValuesContainsRequiresEveryPoint() throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        addExample(w);
        w.forceMerge(1);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher s = searcher(reader);
        // one query point: both paths agree
        assertEquals(List.of("A", "C"), ids(s, points(P1)));
        assertEquals(List.of("A", "C"), ids(s, docValues(P1)));
        // two query points: only the document that holds both
        assertEquals(List.of("C"), ids(s, points(P1, P2)));
        assertEquals(
            "doc values CONTAINS(P1, P2); INTERSECTS(P1, P2) is "
                + ids(s, LatLonPoint.newGeometryQuery(FIELD, QueryRelation.INTERSECTS, P1, P2)),
            List.of("C"),
            ids(s, docValues(P1, P2)));
      }
    }
  }

  /**
   * The same four documents plus 2,000 one-point documents at P1 or P2, so that a selective clause
   * makes the IndexOrDocValuesQuery run its doc-values side.
   */
  public void testIndexOrDocValuesQueryUnderSelectiveLead() throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        addExample(w);
        for (int i = 0; i < 2000; i++) {
          w.addDocument(doc("f" + i, i % 2 == 0 ? P1 : P2));
        }
        w.forceMerge(1);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher s = searcher(reader);
        Query iodv = new IndexOrDocValuesQuery(points(P1, P2), docValues(P1, P2));
        Query leadA =
            new BooleanQuery.Builder()
                .add(new TermQuery(new Term("id", "A")), Occur.FILTER)
                .add(iodv, Occur.FILTER)
                .build();
        int docA = s.search(new TermQuery(new Term("id", "A")), 1).scoreDocs[0].doc;

        assertEquals(List.of("C"), ids(s, points(P1, P2)));
        assertEquals(List.of("C"), ids(s, iodv)); // alone, the wrapper runs the points side
        // the doc-values side on its own, under a selective lead clause, and in explain()
        String actual =
            "docValues hits="
                + ids(s, docValues(P1, P2)).size()
                + ", id:A AND wrapper="
                + ids(s, leadA)
                + ", wrapper explain(A) isMatch="
                + s.explain(iodv, docA).isMatch();
        assertEquals(
            "docValues hits=1, id:A AND wrapper=[], wrapper explain(A) isMatch=false", actual);
      }
    }
  }
}
