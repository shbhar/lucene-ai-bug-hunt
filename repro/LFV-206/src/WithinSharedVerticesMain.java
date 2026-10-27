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
import org.apache.lucene.document.ShapeField.QueryRelation;
import org.apache.lucene.document.XYShape;
import org.apache.lucene.geo.XYLine;
import org.apache.lucene.geo.XYPolygon;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.ByteBuffersDirectory;

/** Framework-free variant of TestPolygonWithinSharedVertices (XY segment WITHIN dart). Exits 1 on failure. */
public class WithinSharedVerticesMain {
  static int count(Field[] fields, Query q) throws Exception {
    ByteBuffersDirectory dir = new ByteBuffersDirectory();
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
    Document doc = new Document();
    for (Field f : fields) doc.add(f);
    w.addDocument(doc);
    w.close();
    DirectoryReader r = DirectoryReader.open(dir);
    int c = new IndexSearcher(r).count(q);
    r.close();
    return c;
  }

  public static void main(String[] args) throws Exception {
    XYPolygon dart = new XYPolygon(new float[] {0, 2, 1, 1, 0}, new float[] {0, 0, 1, 2, 0});
    Field[] fields = XYShape.createIndexableFields("shape", new XYLine(new float[] {2, 1}, new float[] {0, 2}));
    int inter = count(fields, XYShape.newPolygonQuery("shape", QueryRelation.INTERSECTS, dart));
    int within = count(fields, XYShape.newPolygonQuery("shape", QueryRelation.WITHIN, dart));
    System.out.println((inter == 1 ? "ok   " : "FAIL ") + "segment (2,0)-(1,2) INTERSECTS dart: " + inter);
    System.out.println((within == 0 ? "ok   " : "FAIL ") + "segment (2,0)-(1,2) is not WITHIN dart (passes (1.5,1), outside): " + within);
    System.exit(inter == 1 && within == 0 ? 0 : 1);
  }
}
