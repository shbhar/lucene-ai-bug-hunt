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
import org.apache.lucene.document.LatLonDocValuesField;
import org.apache.lucene.document.LatLonPoint;
import org.apache.lucene.document.ShapeField.QueryRelation;
import org.apache.lucene.geo.GeoEncodingUtils;
import org.apache.lucene.geo.Point;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

/** main() port of TestLatLonDocValuesContainsMultiPoint (doc-values leg only). Exit 1 on mismatch. */
public class LatLonDocValuesContainsMultiPointMain {
  public static void main(String[] args) throws Exception {
    double lat1 = GeoEncodingUtils.decodeLatitude(GeoEncodingUtils.encodeLatitude(10));
    double lon1 = GeoEncodingUtils.decodeLongitude(GeoEncodingUtils.encodeLongitude(20));
    double lat2 = GeoEncodingUtils.decodeLatitude(GeoEncodingUtils.encodeLatitude(30));
    double lon2 = GeoEncodingUtils.decodeLongitude(GeoEncodingUtils.encodeLongitude(40));
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (int i = 0; i < 1000; i++) {
          Document doc = new Document();
          double lat = i % 2 == 0 ? lat1 : lat2, lon = i % 2 == 0 ? lon1 : lon2;
          doc.add(new LatLonPoint("p", lat, lon));
          doc.add(new LatLonDocValuesField("p", lat, lon));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        s.setQueryCache(null);
        Point[] q = {new Point(lat1, lon1), new Point(lat2, lon2)};
        Query points = LatLonPoint.newGeometryQuery("p", QueryRelation.CONTAINS, q);
        Query dv = LatLonDocValuesField.newSlowGeometryQuery("p", QueryRelation.CONTAINS, q);
        int pts = s.search(points, 2000).scoreDocs.length;
        int dvs = s.search(dv, 2000).scoreDocs.length;
        System.out.println("CONTAINS(both points): LatLonPoint=" + pts + " LatLonDocValuesField=" + dvs + " (expected 0 and 0)");
        if (pts != 0 || dvs != 0) {
          System.out.println("FAIL");
          System.exit(1);
        }
        System.out.println("PASS");
      }
    }
  }
}
