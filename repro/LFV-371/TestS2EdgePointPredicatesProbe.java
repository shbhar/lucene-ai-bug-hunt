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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.spatial.prefix.tree.S2PrefixTree;
import org.apache.lucene.spatial.query.SpatialArgs;
import org.apache.lucene.spatial.query.SpatialOperation;
import org.apache.lucene.spatial.spatial4j.Geo3dSpatialContextFactory;
import org.apache.lucene.spatial3d.geom.PlanetModel;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Point;
import org.locationtech.spatial4j.shape.Shape;

/**
 * Scope probe for the S2 point fix: for query points on S2 cell edges (and one off-edge control),
 * index the point itself plus rectangles and a circle that touch or cover it from every side, and
 * compare Intersects(P), Contains(P) and IsWithin(P) with SpatialOperation.evaluate. Reports false
 * negatives (evaluate true, not found) and extra hits (found, evaluate false; allowed for RPT's
 * grid approximation). Fails if any false negative is found. Public API only.
 */
public class TestS2EdgePointPredicatesProbe extends LuceneTestCase {

  public void testEdgePointPredicates() throws Exception {
    double[][] lonLat = {
      {10, 0}, {160, 0}, {-73.5, 0}, {45, 20}, {90, 10}, {0, 0}, {10.3, 0.7} // last: off-edge
    };
    SpatialOperation[] ops = {
      SpatialOperation.Intersects, SpatialOperation.Contains, SpatialOperation.IsWithin
    };
    long totalFalseNeg = 0;
    List<String> falseNegExamples = new ArrayList<>();
    for (PlanetModel pm : new PlanetModel[] {PlanetModel.SPHERE, PlanetModel.WGS84}) {
      Geo3dSpatialContextFactory f = new Geo3dSpatialContextFactory();
      f.planetModel = pm;
      SpatialContext ctx = f.newSpatialContext();
      for (int[] cfg : new int[][] {{1, 8}, {1, 31}, {2, 16}, {3, 11}}) {
        int arity = cfg[0];
        int levels = cfg[1];
        RecursivePrefixTreeStrategy st =
            new RecursivePrefixTreeStrategy(new S2PrefixTree(ctx, levels, arity), "geo");
        List<Shape> docs = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (double[] ll : lonLat) {
          double x = ll[0];
          double y = ll[1];
          String at = "(" + x + "," + y + ") ";
          docs.add(ctx.getShapeFactory().pointXY(x, y));
          names.add(at + "point");
          docs.add(ctx.getShapeFactory().rect(x - 1, x + 1, y, y + 1));
          names.add(at + "rect north, P on its south edge");
          docs.add(ctx.getShapeFactory().rect(x - 1, x + 1, y - 1, y));
          names.add(at + "rect south, P on its north edge");
          docs.add(ctx.getShapeFactory().rect(x, x + 1, y - 1, y + 1));
          names.add(at + "rect east, P on its west edge");
          docs.add(ctx.getShapeFactory().rect(x - 1, x, y - 1, y + 1));
          names.add(at + "rect west, P on its east edge");
          docs.add(ctx.getShapeFactory().rect(x, x + 1, y, y + 1));
          names.add(at + "rect NE, P at its corner");
          docs.add(ctx.getShapeFactory().rect(x - 1, x, y - 1, y));
          names.add(at + "rect SW, P at its corner");
          docs.add(ctx.getShapeFactory().circle(ctx.getShapeFactory().pointXY(x, y), 1));
          names.add(at + "circle r=1 around P");
        }
        try (Directory dir = new ByteBuffersDirectory()) {
          try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
            for (int i = 0; i < docs.size(); i++) {
              Document d = new Document();
              for (Field fl : st.createIndexableFields(docs.get(i))) {
                d.add(fl);
              }
              d.add(new StoredField("id", i));
              w.addDocument(d);
            }
          }
          try (DirectoryReader r = DirectoryReader.open(dir)) {
            IndexSearcher s = new IndexSearcher(r);
            Map<SpatialOperation, Set<String>> truthSets = new HashMap<>();
            for (SpatialOperation op : ops) {
              Set<String> truthSet = new HashSet<>();
              truthSets.put(op, truthSet);
              int truth = 0;
              int found = 0;
              int falseNeg = 0;
              int extra = 0;
              for (double[] ll : lonLat) {
                Point q = ctx.getShapeFactory().pointXY(ll[0], ll[1]);
                Set<Integer> hits = new HashSet<>();
                for (ScoreDoc sd : s.search(st.makeQuery(new SpatialArgs(op, q)), 10_000).scoreDocs) {
                  hits.add(s.storedFields().document(sd.doc).getField("id").numericValue().intValue());
                }
                for (int i = 0; i < docs.size(); i++) {
                  boolean expected = op.evaluate(docs.get(i), q);
                  boolean got = hits.contains(i);
                  if (expected) {
                    truth++;
                    truthSet.add(ll[0] + "," + ll[1] + "|" + i);
                  }
                  if (got) {
                    found++;
                  }
                  if (expected && !got) {
                    falseNeg++;
                    if (falseNegExamples.size() < 40) {
                      falseNegExamples.add(
                          pm + " arity=" + arity + " levels=" + levels + " " + op + " q=(" + ll[0]
                              + "," + ll[1] + ") missed doc " + names.get(i));
                    }
                  }
                  if (got && !expected) {
                    extra++;
                  }
                }
              }
              totalFalseNeg += falseNeg;
              System.out.println(
                  "PROBE "
                      + pm
                      + " arity="
                      + arity
                      + " levels="
                      + levels
                      + " op="
                      + op
                      + " truePairs="
                      + truth
                      + " found="
                      + found
                      + " falseNeg="
                      + falseNeg
                      + " extra="
                      + extra);
            }
            System.out.println(
                "SAME-TRUTH "
                    + pm
                    + " arity="
                    + arity
                    + " levels="
                    + levels
                    + " Intersects(P) and Contains(P) have the same true pairs: "
                    + truthSets
                        .get(SpatialOperation.Intersects)
                        .equals(truthSets.get(SpatialOperation.Contains)));
          }
        }
      }
    }
    for (String e : falseNegExamples) {
      System.out.println("FALSE-NEG " + e);
    }
    assertEquals("false negatives (first 40 listed above)", 0, totalFalseNeg);
  }
}
