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
package org.apache.lucene.spatial;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.DoubleValues;
import org.apache.lucene.spatial.bbox.BBoxStrategy;
import org.apache.lucene.spatial.prefix.RecursivePrefixTreeStrategy;
import org.apache.lucene.spatial.prefix.tree.QuadPrefixTree;
import org.apache.lucene.spatial.serialized.SerializedDVStrategy;
import org.apache.lucene.spatial.vector.PointVectorStrategy;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Point;
import org.locationtech.spatial4j.shape.Shape;

/**
 * {@link SpatialStrategy#makeRecipDistanceValueSource} documents: "'zScaling' is one tenth the
 * distance to the farthest edge from the center. Thus the scores will be 1 for indexed points at
 * the center of the query shape and as low as ~0.1 at its furthest edges." The edge tolerance is
 * the one {@code TestDistanceStrategy#testRecipScore} uses: 0.10 +- 0.09.
 */
public class TestRecipDistanceWideGeoBox extends LuceneTestCase {

  private static final SpatialContext CTX = SpatialContext.GEO;

  private static List<SpatialStrategy> strategies() {
    return List.of(
        PointVectorStrategy.newInstance(CTX, "pv"),
        BBoxStrategy.newInstance(CTX, "bbox"),
        new SerializedDVStrategy(CTX, "ser"),
        new RecursivePrefixTreeStrategy(new QuadPrefixTree(CTX, 25), "rpt"));
  }

  /** Indexes the query center and {@code edge}, returns their recip-distance values. */
  private static double[] centerAndEdge(SpatialStrategy strategy, Shape query, Point edge)
      throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        for (Point p : new Point[] {query.getCenter(), edge}) {
          Document doc = new Document();
          for (Field f : strategy.createIndexableFields(p)) {
            doc.add(f);
          }
          w.addDocument(doc);
        }
        w.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        LeafReaderContext leaf = reader.leaves().get(0);
        DoubleValues v = strategy.makeRecipDistanceValueSource(query).getValues(leaf, null);
        double[] scores = new double[2];
        for (int doc = 0; doc < 2; doc++) {
          assertTrue(v.advanceExact(doc));
          scores[doc] = v.doubleValue();
        }
        return scores;
      }
    }
  }

  /** Checks the documented scores (1 at the center, ~0.1 at {@code edge}) on every strategy. */
  private static void assertDocumentedScores(Shape query, Point edge, double edgeDist)
      throws IOException {
    assertEquals(edgeDist, CTX.getDistCalc().distance(query.getCenter(), edge), 1e-9);
    double documented = 0.1 * edgeDist / (edgeDist + 0.1 * edgeDist); // 1/11 = 0.0909
    StringBuilder failures = new StringBuilder();
    for (SpatialStrategy strategy : strategies()) {
      double[] s = centerAndEdge(strategy, query, edge);
      if (Math.abs(s[0] - 1.0) > 0.01 || Math.abs(s[1] - 0.10) > 0.09) {
        failures.append(
            String.format(
                Locale.ROOT,
                "%s: center %.5f, edge %s (%.1f degrees away) %.5f, documented %.5f%n",
                strategy.getClass().getSimpleName(),
                s[0],
                edge,
                edgeDist,
                s[1],
                documented));
      }
    }
    assertTrue(query + " scores outside 0.10 +- 0.09:\n" + failures, failures.length() == 0);
  }

  /** A box 340 degrees wide: (170, 0) on its east edge is 170 degrees from the center (0, 0). */
  public void testBoxWiderThan180Degrees() throws IOException {
    assertDocumentedScores(
        CTX.getShapeFactory().rect(-170, 170, -10, 10), CTX.getShapeFactory().pointXY(170, 0), 170);
  }

  /** The band of all longitudes from 5 S to 5 N: (180, 5) on its north edge is 175 degrees away. */
  public void testFullLongitudeBand() throws IOException {
    assertDocumentedScores(
        CTX.getShapeFactory().rect(-180, 180, -5, 5), CTX.getShapeFactory().pointXY(180, 5), 175);
  }

  /** Control: a box 120 degrees wide; its corner (60, 10) is 60.5 degrees away. Passes. */
  public void testControlNarrowBox() throws IOException {
    Point corner = CTX.getShapeFactory().pointXY(60, 10);
    Shape box = CTX.getShapeFactory().rect(-60, 60, -10, 10);
    assertDocumentedScores(box, corner, CTX.getDistCalc().distance(box.getCenter(), corner));
  }

  /**
   * Control: a circle of radius 6 around (0, 85) contains the north pole, so its bounding box spans
   * all longitudes too. Its edge point due north, past the pole, is (180, 89). Passes today, and a
   * fix must keep it passing.
   */
  public void testControlCircleAroundPole() throws IOException {
    assertDocumentedScores(
        CTX.getShapeFactory().circle(0, 85, 6), CTX.getShapeFactory().pointXY(180, 89), 6);
  }
}
