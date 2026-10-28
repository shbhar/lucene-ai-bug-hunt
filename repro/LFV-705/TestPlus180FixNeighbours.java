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
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.spatial.SpatialStrategy;
import org.apache.lucene.spatial.composite.CompositeSpatialStrategy;
import org.apache.lucene.spatial.prefix.tree.PackedQuadPrefixTree;
import org.apache.lucene.spatial.prefix.tree.SpatialPrefixTree;
import org.apache.lucene.spatial.query.SpatialArgs;
import org.apache.lucene.spatial.query.SpatialOperation;
import org.apache.lucene.spatial.serialized.SerializedDVStrategy;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Shape;
import org.locationtech.spatial4j.shape.ShapeFactory;

/**
 * Side-effect probe for the query-side fix (getCell maps x=+180 to -180 in a geo context): indexes
 * points, rectangles and circles at and around the antimeridian and runs point (and one rectangle)
 * queries at +/-180 with RPT (pointsOnly false and true where legal) and the default composite
 * strategy. A false negative is a document for which SpatialOperation.evaluate(indexed, query) is
 * true but the query returns 0 hits. Prints every false negative, then asserts there are none.
 */
public class TestPlus180FixNeighbours extends LuceneTestCase {

  private static final SpatialContext CTX = SpatialContext.GEO;
  private static final ShapeFactory F = CTX.getShapeFactory();

  private static int count(SpatialStrategy s, Shape indexed, SpatialOperation op, Shape query)
      throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
      Document doc = new Document();
      for (Field f : s.createIndexableFields(indexed)) {
        doc.add(f);
      }
      w.addDocument(doc);
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(w)) {
        return new IndexSearcher(r).count(s.makeQuery(new SpatialArgs(op, query)));
      }
    }
  }

  public void testNoFalseNegativesAroundTheAntimeridian() throws IOException {
    List<Shape> pointDocs = new ArrayList<>();
    List<Shape> otherDocs = new ArrayList<>();
    double[] ys = {-90, -45.5, 0, 10, 89.9, 90};
    for (double y : ys) {
      pointDocs.add(F.pointXY(180, y));
      pointDocs.add(F.pointXY(-180, y));
    }
    otherDocs.add(F.rect(179, 180, 9, 11));
    otherDocs.add(F.rect(-180, -179, 9, 11));
    otherDocs.add(F.rect(170, -170, 9, 11)); // crosses the antimeridian
    otherDocs.add(F.rect(180, 180, 9, 11)); // zero width on +180
    otherDocs.add(F.rect(-180, 180, -90, 90)); // whole world
    otherDocs.add(F.circle(F.pointXY(180, 10), 1));
    otherDocs.add(F.circle(F.pointXY(179.5, 10), 0.5));
    otherDocs.add(F.circle(F.pointXY(-179.5, 10), 0.5));
    otherDocs.add(F.circle(F.pointXY(178, 10), 5));

    List<Shape> queries = new ArrayList<>();
    for (double y : ys) {
      queries.add(F.pointXY(180, y));
      queries.add(F.pointXY(-180, y));
    }
    queries.add(F.rect(179, 180, 9, 11));
    queries.add(F.rect(-180, -179, 9, 11));

    SpatialOperation[] ops = {
      SpatialOperation.Intersects, SpatialOperation.Contains, SpatialOperation.IsWithin
    };
    int[] levels = {8, 26};
    int cases = 0;
    int falseNeg = 0;
    StringBuilder sb = new StringBuilder();
    for (int levelsMax : levels) {
      for (int strat = 0; strat < 3; strat++) {
        SpatialPrefixTree grid = new PackedQuadPrefixTree(CTX, levelsMax);
        boolean pointsOnly = strat == 1;
        RecursivePrefixTreeStrategy rpt = new RecursivePrefixTreeStrategy(grid, "geo");
        rpt.setPointsOnly(pointsOnly);
        SpatialStrategy s;
        String name;
        if (strat == 2) {
          s = new CompositeSpatialStrategy("geo", rpt, new SerializedDVStrategy(CTX, "geo_sdv"));
          name = "composite(rpt pointsOnly=false)";
        } else {
          s = rpt;
          name = "rpt pointsOnly=" + pointsOnly;
        }
        List<Shape> docs = new ArrayList<>(pointDocs);
        if (pointsOnly == false) {
          docs.addAll(otherDocs);
        }
        for (Shape indexed : docs) {
          for (Shape q : queries) {
            for (SpatialOperation op : ops) {
              if (pointsOnly && op != SpatialOperation.Intersects) {
                continue; // RPT pointsOnly supports Intersects only
              }
              boolean exact = op.evaluate(indexed, q);
              if (exact == false) {
                continue;
              }
              cases++;
              int hits = count(s, indexed, op, q);
              if (hits == 0) {
                falseNeg++;
                sb.append("FN levels=")
                    .append(levelsMax)
                    .append(' ')
                    .append(name)
                    .append(' ')
                    .append(op)
                    .append(" doc=")
                    .append(indexed)
                    .append(" query=")
                    .append(q)
                    .append('\n');
              }
            }
          }
        }
      }
    }
    System.out.println(sb);
    System.out.println("PROBE cases(exact match expected)=" + cases + " falseNegatives=" + falseNeg);
    assertEquals("false negatives (see output)", 0, falseNeg);
  }
}
