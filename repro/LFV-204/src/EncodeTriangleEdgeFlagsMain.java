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
import org.apache.lucene.document.ShapeField;
import org.apache.lucene.document.ShapeField.QueryRelation;
import org.apache.lucene.document.XYShape;
import org.apache.lucene.geo.XYPolygon;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.ByteBuffersDirectory;

/**
 * Framework-free variant of TestEncodeTriangleEdgeFlags (runs on Lucene 8.x too). Exits 1 when
 * any check fails.
 */
public class EncodeTriangleEdgeFlagsMain {
  static int failures = 0;

  static void check(boolean ok, String msg) {
    System.out.println((ok ? "ok   " : "FAIL ") + msg);
    if (!ok) failures++;
  }

  static int count(XYPolygon polygon, QueryRelation rel, float minX, float maxX, float minY, float maxY)
      throws Exception {
    ByteBuffersDirectory dir = new ByteBuffersDirectory();
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
    Document doc = new Document();
    for (Field f : XYShape.createIndexableFields("shape", polygon)) doc.add(f);
    w.addDocument(doc);
    w.close();
    DirectoryReader reader = DirectoryReader.open(dir);
    int c = new IndexSearcher(reader).count(XYShape.newBoxQuery("shape", rel, minX, maxX, minY, maxY));
    reader.close();
    return c;
  }

  public static void main(String[] args) throws Exception {
    // 1. encoding level: CW triangle (0,0) (0,10) (10,0); only edge (0,10)-(10,0) belongs to shape
    byte[] bytes = new byte[28];
    ShapeField.encodeTriangle(bytes, 0, 0, false, 10, 0, true, 0, 10, false);
    ShapeField.DecodedTriangle d = new ShapeField.DecodedTriangle();
    ShapeField.decodeTriangle(bytes, d);
    int[][] v = {{d.aX, d.aY}, {d.bX, d.bY}, {d.cX, d.cY}};
    boolean[] flag = {d.ab, d.bc, d.ca};
    boolean ok = true;
    for (int e = 0; e < 3; e++) {
      int[] u = v[e], w = v[(e + 1) % 3];
      boolean isP1P2 =
          (u[0] == 0 && u[1] == 10 && w[0] == 10 && w[1] == 0)
              || (u[0] == 10 && u[1] == 0 && w[0] == 0 && w[1] == 10);
      ok &= isP1P2 == flag[e];
    }
    check(ok, "decoded edge flags follow their edges: " + d);

    // 2. false positive: box centred on boundary edge (-1,7)-(-8,8)
    XYPolygon p1 = new XYPolygon(new float[] {6, -1, -8, 0, 6}, new float[] {0, 7, 8, 1, 0});
    check(count(p1, QueryRelation.INTERSECTS, -4.75f, -4.25f, 7.25f, 7.75f) == 1, "p1 INTERSECTS box == 1");
    int c1 = count(p1, QueryRelation.CONTAINS, -4.75f, -4.25f, 7.25f, 7.75f);
    check(c1 == 0, "p1 CONTAINS box straddling its boundary: expected 0, got " + c1);

    // 3. false negative: box strictly inside, on the tessellation diagonal (6,0)-(1,8)
    XYPolygon p2 = new XYPolygon(new float[] {3, 6, 8, 1, 3}, new float[] {3, 0, 5, 8, 3});
    check(count(p2, QueryRelation.INTERSECTS, 3.25f, 3.75f, 3.75f, 4.25f) == 1, "p2 INTERSECTS box == 1");
    int c2 = count(p2, QueryRelation.CONTAINS, 3.25f, 3.75f, 3.75f, 4.25f);
    check(c2 == 1, "p2 CONTAINS box strictly inside it: expected 1, got " + c2);
    System.exit(failures == 0 ? 0 : 1);
  }
}
