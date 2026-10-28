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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LatLonDocValuesField;
import org.apache.lucene.document.LatLonPoint;
import org.apache.lucene.document.ShapeField.QueryRelation;
import org.apache.lucene.document.StringField;
import org.apache.lucene.geo.GeoEncodingUtils;
import org.apache.lucene.geo.Point;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexOrDocValuesQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.Version;

/**
 * Version-portable main() form of TestLatLonDocValuesContainsMultiPoint, for the version-history runs.
 * Uses only API present from 8.8.0 to main. 1000 one-point documents: even ones at P1, odd ones at
 * P2 (grid-exact), plus an "id" term. CONTAINS(P1, P2) must match no document on every path.
 *
 * <p>Exit 0 = correct (no document matches CONTAINS(P1, P2) on any path); exit 1 = bug (doc-values
 * path or IndexOrDocValuesQuery under a selective lead matches documents holding only one point);
 * exit 3 = sanity failure (single-point CONTAINS counts or the points-index result are not as
 * expected, so the run says nothing about the bug).
 */
public class LatLonDocValuesContainsHistoryMain {
  public static void main(String[] args) throws Exception {
    double lat1 = GeoEncodingUtils.decodeLatitude(GeoEncodingUtils.encodeLatitude(10));
    double lon1 = GeoEncodingUtils.decodeLongitude(GeoEncodingUtils.encodeLongitude(20));
    double lat2 = GeoEncodingUtils.decodeLatitude(GeoEncodingUtils.encodeLatitude(30));
    double lon2 = GeoEncodingUtils.decodeLongitude(GeoEncodingUtils.encodeLongitude(40));
    System.out.println("lucene Version.LATEST=" + Version.LATEST);
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (int i = 0; i < 1000; i++) {
          Document doc = new Document();
          doc.add(new StringField("id", Integer.toString(i), Field.Store.NO));
          double lat = i % 2 == 0 ? lat1 : lat2, lon = i % 2 == 0 ? lon1 : lon2;
          doc.add(new LatLonPoint("p", lat, lon));
          doc.add(new LatLonDocValuesField("p", lat, lon));
          w.addDocument(doc);
        }
        w.forceMerge(1);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        s.setQueryCache(null);
        Point p1 = new Point(lat1, lon1), p2 = new Point(lat2, lon2);
        Point[] q = {p1, p2};
        int ptsP1 = hits(s, LatLonPoint.newGeometryQuery("p", QueryRelation.CONTAINS, p1));
        int ptsP2 = hits(s, LatLonPoint.newGeometryQuery("p", QueryRelation.CONTAINS, p2));
        int dvP1 = hits(s, LatLonDocValuesField.newSlowGeometryQuery("p", QueryRelation.CONTAINS, p1));
        int dvP2 = hits(s, LatLonDocValuesField.newSlowGeometryQuery("p", QueryRelation.CONTAINS, p2));
        Query points = LatLonPoint.newGeometryQuery("p", QueryRelation.CONTAINS, q);
        Query dv = LatLonDocValuesField.newSlowGeometryQuery("p", QueryRelation.CONTAINS, q);
        int pts = hits(s, points);
        int dvs = hits(s, dv);
        Query iodv =
            new BooleanQuery.Builder()
                .add(new TermQuery(new Term("id", "7")), BooleanClause.Occur.MUST)
                .add(new IndexOrDocValuesQuery(points, dv), BooleanClause.Occur.FILTER)
                .build();
        int iodvs = hits(s, iodv);
        // same with the id clause as FILTER: the lead cost handed to IndexOrDocValuesQuery is then
        // the id clause's cost on every version (10.0-10.2 computed it from FILTER clauses only)
        Query iodvFilterLead =
            new BooleanQuery.Builder()
                .add(new TermQuery(new Term("id", "7")), BooleanClause.Occur.FILTER)
                .add(new IndexOrDocValuesQuery(points, dv), BooleanClause.Occur.FILTER)
                .build();
        int iodvFs = hits(s, iodvFilterLead);
        System.out.println(
            "single-point CONTAINS: points(P1)="
                + ptsP1
                + " points(P2)="
                + ptsP2
                + " docValues(P1)="
                + dvP1
                + " docValues(P2)="
                + dvP2
                + " (expected 500 each)");
        System.out.println(
            "CONTAINS(P1,P2): LatLonPoint="
                + pts
                + " LatLonDocValuesField="
                + dvs
                + " MUST(id:7)+FILTER(IndexOrDocValuesQuery(points,docValues))="
                + iodvs
                + " FILTER(id:7)+FILTER(IndexOrDocValuesQuery(points,docValues))="
                + iodvFs
                + " (expected 0, 0, 0, 0)");
        if (ptsP1 != 500 || ptsP2 != 500 || dvP1 != 500 || dvP2 != 500 || pts != 0) {
          System.out.println("SANITY-FAIL");
          System.exit(3);
        }
        if (dvs != 0 || iodvs != 0 || iodvFs != 0) {
          System.out.println("FAIL (bug present)");
          System.exit(1);
        }
        System.out.println("PASS (bug absent)");
      }
    }
  }

  private static int hits(IndexSearcher s, Query q) throws java.io.IOException {
    return s.search(q, 2000).scoreDocs.length;
  }
}
