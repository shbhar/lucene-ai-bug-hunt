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
import org.apache.lucene.geo.Polygon;
import org.apache.lucene.geo.XYPolygon;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

/** main() port of TestContainsQueryPolygonHoles for older releases. Exit 1 on mismatch. */
public class ContainsQueryPolygonHolesMain {
  public static void main(String[] args) throws Exception {
    int bad = 0;
    XYPolygon xq = new XYPolygon(new float[] {0, 0, 10, 10, 0}, new float[] {0, 10, 10, 0, 0},
        new XYPolygon(new float[] {1, 3, 3, 1, 1}, new float[] {4, 4, 6, 6, 4}),
        new XYPolygon(new float[] {7, 9, 9, 7, 7}, new float[] {4, 4, 6, 6, 4}));
    XYPolygon xh = new XYPolygon(new float[] {2, 8, 8, 2, 2}, new float[] {4.5f, 4.5f, 5.5f, 5.5f, 4.5f});
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        add(w, XYShape.createIndexableFields("f", new XYPolygon(new float[] {-1, -1, 11, 11, -1}, new float[] {-1, 11, 11, -1, -1}, xh)));
        add(w, XYShape.createIndexableFields("f", new XYPolygon(new float[] {-1, -1, 11, 11, -1}, new float[] {-1, 11, 11, -1, -1})));
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        String h = hits(new IndexSearcher(r), XYShape.newPolygonQuery("f", QueryRelation.CONTAINS, xq));
        System.out.println("XYShape     CONTAINS hits=" + h + " (expected [1])");
        if (!h.equals("[1]")) bad++;
      }
    }
    Polygon lq = new Polygon(new double[] {0, 10, 10, 0, 0}, new double[] {0, 0, 10, 10, 0},
        new Polygon(new double[] {4, 4, 6, 6, 4}, new double[] {1, 3, 3, 1, 1}),
        new Polygon(new double[] {4, 4, 6, 6, 4}, new double[] {7, 9, 9, 7, 7}));
    Polygon lh = new Polygon(new double[] {4.5, 4.5, 5.5, 5.5, 4.5}, new double[] {2, 8, 8, 2, 2});
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        add(w, LatLonShape.createIndexableFields("f", new Polygon(new double[] {-1, 11, 11, -1, -1}, new double[] {-1, -1, 11, 11, -1}, lh)));
        add(w, LatLonShape.createIndexableFields("f", new Polygon(new double[] {-1, 11, 11, -1, -1}, new double[] {-1, -1, 11, 11, -1})));
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        String h = hits(new IndexSearcher(r), LatLonShape.newPolygonQuery("f", QueryRelation.CONTAINS, lq));
        System.out.println("LatLonShape CONTAINS hits=" + h + " (expected [1])");
        if (!h.equals("[1]")) bad++;
      }
    }
    if (bad > 0) {
      System.out.println("FAIL");
      System.exit(1);
    }
    System.out.println("PASS");
  }

  static String hits(IndexSearcher s, org.apache.lucene.search.Query q) throws Exception {
    StringBuilder b = new StringBuilder("[");
    for (ScoreDoc sd : s.search(q, 10, Sort.INDEXORDER).scoreDocs) {
      if (b.length() > 1) b.append(", ");
      b.append(sd.doc);
    }
    return b.append("]").toString();
  }

  static void add(IndexWriter w, Field[] fields) throws Exception {
    Document d = new Document();
    for (Field f : fields) d.add(f);
    w.addDocument(d);
  }
}
