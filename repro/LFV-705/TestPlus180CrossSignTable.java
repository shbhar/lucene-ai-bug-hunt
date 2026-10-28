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
package org.apache.lucene.spatial.prefix;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.spatial.SpatialStrategy;
import org.apache.lucene.spatial.composite.CompositeSpatialStrategy;
import org.apache.lucene.spatial.prefix.tree.PackedQuadPrefixTree;
import org.apache.lucene.spatial.query.SpatialArgs;
import org.apache.lucene.spatial.query.SpatialOperation;
import org.apache.lucene.spatial.serialized.SerializedDVStrategy;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Point;

/**
 * Print-only table: hits for a document at x = +180 or -180 (y = 10) queried with a point at x =
 * +180 or -180, per strategy and operation, on PackedQuadPrefixTree(GEO, 8). Shows the
 * workaround (write +180 as -180 on both sides) and what the fix changes for mixed signs.
 */
public class TestPlus180CrossSignTable extends LuceneTestCase {

  private static final SpatialContext CTX = SpatialContext.GEO;

  private static int count(SpatialStrategy s, Point doc, SpatialOperation op, Point q)
      throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
      Document d = new Document();
      for (Field f : s.createIndexableFields(doc)) {
        d.add(f);
      }
      w.addDocument(d);
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(w)) {
        return new IndexSearcher(r).count(s.makeQuery(new SpatialArgs(op, q)));
      }
    }
  }

  public void testTable() throws IOException {
    StringBuilder sb = new StringBuilder("TABLE strategy | op | doc x | query x | exact | hits\n");
    for (int strat = 0; strat < 4; strat++) {
      boolean pointsOnly = strat % 2 == 1;
      RecursivePrefixTreeStrategy rpt =
          new RecursivePrefixTreeStrategy(new PackedQuadPrefixTree(CTX, 8), "geo");
      rpt.setPointsOnly(pointsOnly);
      SpatialStrategy s =
          strat < 2 ? rpt : new CompositeSpatialStrategy("geo", rpt, new SerializedDVStrategy(CTX, "sdv"));
      String name = (strat < 2 ? "RPT" : "composite") + "(pointsOnly=" + pointsOnly + ")";
      for (SpatialOperation op : new SpatialOperation[] {SpatialOperation.Intersects, SpatialOperation.Contains}) {
        if (strat == 1 && op != SpatialOperation.Intersects) {
          continue;
        }
        for (double dx : new double[] {180, -180}) {
          for (double qx : new double[] {180, -180}) {
            Point doc = CTX.getShapeFactory().pointXY(dx, 10);
            Point q = CTX.getShapeFactory().pointXY(qx, 10);
            sb.append("TABLE ").append(name).append(" | ").append(op).append(" | ").append(dx)
                .append(" | ").append(qx).append(" | ").append(op.evaluate(doc, q)).append(" | ")
                .append(count(s, doc, op, q)).append('\n');
          }
        }
      }
    }
    System.out.println(sb);
  }
}
