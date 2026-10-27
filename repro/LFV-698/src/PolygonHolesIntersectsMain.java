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
import org.apache.lucene.document.LatLonShape;
import org.apache.lucene.document.ShapeField.QueryRelation;
import org.apache.lucene.document.XYShape;
import org.apache.lucene.geo.Line;
import org.apache.lucene.geo.Polygon;
import org.apache.lucene.geo.XYLine;
import org.apache.lucene.geo.XYPolygon;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

/**
 * Same geometry as TestPolygonHolesIntersects, as a main() for releases whose test framework
 * differs. Square [0,10]^2 minus holes [1,3]x[4,6] and [7,9]x[4,6]; docs: line (2,5)-(8,5), box
 * [2,8]x[4.5,5.5], point (5,5). Expected INTERSECTS = 3 and DISJOINT = 0. Exit 1 on mismatch.
 */
public class PolygonHolesIntersectsMain {
  public static void main(String[] args) throws Exception {
    int bad = 0;
    // XY
    XYPolygon xq =
        new XYPolygon(
            new float[] {0, 0, 10, 10, 0},
            new float[] {0, 10, 10, 0, 0},
            new XYPolygon(new float[] {1, 3, 3, 1, 1}, new float[] {4, 4, 6, 6, 4}),
            new XYPolygon(new float[] {7, 9, 9, 7, 7}, new float[] {4, 4, 6, 6, 4}));
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        add(w, XYShape.createIndexableFields("f", new XYLine(new float[] {2, 8}, new float[] {5, 5})));
        add(
            w,
            XYShape.createIndexableFields(
                "f",
                new XYPolygon(
                    new float[] {2, 2, 8, 8, 2}, new float[] {4.5f, 5.5f, 5.5f, 4.5f, 4.5f})));
        add(w, XYShape.createIndexableFields("f", 5f, 5f));
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        int i = s.count(XYShape.newPolygonQuery("f", QueryRelation.INTERSECTS, xq));
        int d = s.count(XYShape.newPolygonQuery("f", QueryRelation.DISJOINT, xq));
        System.out.println("XYShape     INTERSECTS=" + i + " (expected 3)  DISJOINT=" + d + " (expected 0)");
        if (i != 3 || d != 0) bad++;
      }
    }
    // LatLon
    Polygon lq =
        new Polygon(
            new double[] {0, 10, 10, 0, 0},
            new double[] {0, 0, 10, 10, 0},
            new Polygon(new double[] {4, 4, 6, 6, 4}, new double[] {1, 3, 3, 1, 1}),
            new Polygon(new double[] {4, 4, 6, 6, 4}, new double[] {7, 9, 9, 7, 7}));
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        add(w, LatLonShape.createIndexableFields("f", new Line(new double[] {5, 5}, new double[] {2, 8})));
        add(
            w,
            LatLonShape.createIndexableFields(
                "f",
                new Polygon(new double[] {4.5, 5.5, 5.5, 4.5, 4.5}, new double[] {2, 2, 8, 8, 2})));
        add(w, LatLonShape.createIndexableFields("f", 5, 5));
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        int i = s.count(LatLonShape.newPolygonQuery("f", QueryRelation.INTERSECTS, lq));
        int d = s.count(LatLonShape.newPolygonQuery("f", QueryRelation.DISJOINT, lq));
        System.out.println("LatLonShape INTERSECTS=" + i + " (expected 3)  DISJOINT=" + d + " (expected 0)");
        if (i != 3 || d != 0) bad++;
      }
    }
    if (bad > 0) {
      System.out.println("FAIL: " + bad + " of 2 shape families return wrong hits");
      System.exit(1);
    }
    System.out.println("PASS");
  }

  private static void add(IndexWriter w, Field[] fields) throws Exception {
    Document d = new Document();
    for (Field f : fields) d.add(f);
    w.addDocument(d);
  }
}
