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
import org.apache.lucene.geo.Polygon;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.ByteBuffersDirectory;

/**
 * Framework-free variant of TestTessellatorHoleTouchingShell for older releases (box queries only).
 * Shell (lon,lat) (2,0) (1,1) (0,2) (3,3); hole (1,1) (1,2) (2,2) shares the shell vertex (1,1). The
 * box lon [1.2,1.3] x lat [1.7,1.8] lies strictly inside the hole. Exits 1 on failure.
 */
public class HoleTouchingShellMain {
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
    Polygon hole = new Polygon(new double[] {1, 2, 2, 1}, new double[] {1, 1, 2, 1});
    Polygon polygon = new Polygon(new double[] {0, 1, 2, 3, 0}, new double[] {2, 1, 0, 3, 2}, hole);
    Field[] fields;
    try {
      fields = LatLonShape.createIndexableFields("shape", polygon);
    } catch (IllegalArgumentException e) {
      System.out.println("FAIL valid polygon rejected: " + e.getMessage());
      System.exit(1);
      return;
    }
    int control = count(fields, LatLonShape.newBoxQuery("shape", QueryRelation.INTERSECTS, 0.95, 1.05, 2.15, 2.25));
    int inHole = count(fields, LatLonShape.newBoxQuery("shape", QueryRelation.INTERSECTS, 1.7, 1.8, 1.2, 1.3));
    System.out.println((control == 1 ? "ok   " : "FAIL ") + "box inside the polygon matches: " + control);
    System.out.println((inHole == 0 ? "ok   " : "FAIL ") + "box strictly inside the hole does not match: " + inHole);
    System.exit(control == 1 && inHole == 0 ? 0 : 1);
  }
}
