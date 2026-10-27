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
import org.apache.lucene.geo.Polygon;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.ByteBuffersDirectory;

/**
 * Framework-free variant of TestIntersectsCollinearDisjointEdge (LatLonShape polygon query only; the
 * relation enum moved between releases, so it is looked up by name). Exits 1 on failure.
 */
public class IntersectsCollinearMain {
  public static void main(String[] args) throws Exception {
    Polygon tri = new Polygon(new double[] {0, 0, 4, 0}, new double[] {3, 4, 1.5, 3});
    Polygon square = new Polygon(new double[] {0, 0, 2, 2, 0}, new double[] {0, 2, 2, 0, 0});
    ByteBuffersDirectory dir = new ByteBuffersDirectory();
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
    Document doc = new Document();
    for (Field f : LatLonShape.createIndexableFields("shape", tri)) doc.add(f);
    w.addDocument(doc);
    w.close();
    DirectoryReader r = DirectoryReader.open(dir);
    Class<?> rel;
    try {
      rel = Class.forName("org.apache.lucene.document.ShapeField$QueryRelation");
    } catch (ClassNotFoundException e) {
      rel = Class.forName("org.apache.lucene.document.LatLonShape$QueryRelation");
    }
    Object intersects = Enum.valueOf(rel.asSubclass(Enum.class), "INTERSECTS");
    Query q = (Query) LatLonShape.class.getMethod("newPolygonQuery", String.class, rel, Polygon[].class)
        .invoke(null, "shape", intersects, new Polygon[] {square});
    int c = new IndexSearcher(r).count(q);
    r.close();
    System.out.println((c == 0 ? "ok   " : "FAIL ") + "disjoint triangle INTERSECTS square: " + c);
    System.exit(c == 0 ? 0 : 1);
  }
}
