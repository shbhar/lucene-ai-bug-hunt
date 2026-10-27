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
import org.apache.lucene.document.ShapeField.QueryRelation;
import org.apache.lucene.geo.GeoEncodingUtils;
import org.apache.lucene.geo.Point;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexOrDocValuesQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Each of 1000 documents holds one point, indexed both as LatLonPoint and as LatLonDocValuesField,
 * plus an "id" term: even documents hold (10, 20), odd documents hold (30, 40) (both snapped to the
 * encoding grid). A CONTAINS query with the two points (10, 20) and (30, 40) asks for documents
 * whose points contain both query points, so it must match none of them:
 * the points-index query (LatLonPoint.newGeometryQuery, a conjunction of one CONTAINS clause per
 * point) correctly returns 0 hits. The doc-values query (LatLonDocValuesField.newSlowGeometryQuery),
 * which its javadoc pairs with that points query inside an IndexOrDocValuesQuery, returns every
 * document; and IndexOrDocValuesQuery(points, docValues) under a selective "id" clause runs the
 * doc-values side and returns the document.
 */
public class TestLatLonDocValuesContainsMultiPoint extends LuceneTestCase {

  // coordinates on the encoding grid, so indexed and query points compare equal after encoding
  private static final double LAT1 = GeoEncodingUtils.decodeLatitude(GeoEncodingUtils.encodeLatitude(10));
  private static final double LON1 = GeoEncodingUtils.decodeLongitude(GeoEncodingUtils.encodeLongitude(20));
  private static final double LAT2 = GeoEncodingUtils.decodeLatitude(GeoEncodingUtils.encodeLatitude(30));
  private static final double LON2 = GeoEncodingUtils.decodeLongitude(GeoEncodingUtils.encodeLongitude(40));

  /** number of collected hits (not IndexSearcher#count, which may answer from index statistics) */
  private static int hits(IndexSearcher s, Query q) throws IOException {
    return s.search(q, 2000).scoreDocs.length;
  }

  public void testContainsTwoPointsDocValuesMatchesDocWithOnlyOne() throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (int i = 0; i < 1000; i++) {
          Document doc = new Document();
          doc.add(new StringField("id", Integer.toString(i), Field.Store.NO));
          // even documents hold point 1, odd documents hold point 2: no document holds both
          double lat = i % 2 == 0 ? LAT1 : LAT2, lon = i % 2 == 0 ? LON1 : LON2;
          doc.add(new LatLonPoint("p", lat, lon));
          doc.add(new LatLonDocValuesField("p", lat, lon));
          w.addDocument(doc);
        }
        w.forceMerge(1);
      }
      try (IndexReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        // no query cache: a cached entry would be computed by IndexOrDocValuesQuery's bulk scorer,
        // which always runs the points side
        s.setQueryCache(null);
        Point[] query = {new Point(LAT1, LON1), new Point(LAT2, LON2)};
        Query points = LatLonPoint.newGeometryQuery("p", QueryRelation.CONTAINS, query);
        Query docValues =
            LatLonDocValuesField.newSlowGeometryQuery("p", QueryRelation.CONTAINS, query);
        // sanity: each query point on its own
        for (Point p : query) {
          assertEquals(500, hits(s, LatLonPoint.newGeometryQuery("p", QueryRelation.CONTAINS, p)));
          assertEquals(
              500, hits(s, LatLonDocValuesField.newSlowGeometryQuery("p", QueryRelation.CONTAINS, p)));
        }
        // no document holds both points, so none contains both
        assertEquals("points index CONTAINS(both points)", 0, hits(s, points));
        Query iodv =
            new BooleanQuery.Builder()
                .add(new TermQuery(new Term("id", "7")), BooleanClause.Occur.MUST)
                .add(new IndexOrDocValuesQuery(points, docValues), BooleanClause.Occur.FILTER)
                .build();
        int iodvHits = hits(s, iodv);
        int dvHits = hits(s, docValues);
        assertEquals(
            "doc values CONTAINS(both points) = "
                + dvHits
                + ", id:7 AND IndexOrDocValuesQuery(points, doc values) = "
                + iodvHits,
            0,
            dvHits);
        assertEquals("id:7 AND IndexOrDocValuesQuery(points, doc values)", 0, iodvHits);
      }
    }
  }
}
