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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.function.FunctionScoreQuery;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.DoubleValues;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.spatial.bbox.BBoxStrategy;
import org.apache.lucene.spatial.prefix.RecursivePrefixTreeStrategy;
import org.apache.lucene.spatial.prefix.tree.QuadPrefixTree;
import org.apache.lucene.spatial.serialized.SerializedDVStrategy;
import org.apache.lucene.spatial.vector.PointVectorStrategy;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Point;
import org.locationtech.spatial4j.shape.Rectangle;
import org.locationtech.spatial4j.shape.Shape;

/**
 * SpatialStrategy#makeRecipDistanceValueSource javadoc: "The formula is zScaling/(d + zScaling) where 'd' is the
 * distance and 'zScaling' is one tenth the distance to the farthest edge from the center. Thus the scores will be 1
 * for indexed points at the center of the query shape and as low as ~0.1 at its furthest edges."
 *
 * <p>The tolerance is the one upstream TestDistanceStrategy#testRecipScore uses for the edge: 0.10 +- 0.09.
 *
 * <p>Query shape: the band of all longitudes between 5 S and 5 N, {@code rect(-180, 180, -5, 5)}. Its center is
 * (0, 0); (180, 5) lies on its northern edge (and is a corner of the rectangle), 175 degrees from the center. The
 * documented zScaling is about 17.5, so the score at (180, 5) should be about 0.09.
 */
public class TestRecipDistanceWideQueryShape extends LuceneTestCase {

  private static final SpatialContext CTX = SpatialContext.GEO;

  private static double[] centerAndEdgeScores(SpatialStrategy strategy, Shape query, Point edge)
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

  /** Minimal: PointVectorStrategy, the 5S..5N band, a point on its northern edge. */
  public void testBandFurthestEdgeScore() throws IOException {
    Rectangle band = CTX.getShapeFactory().rect(-180, 180, -5, 5);
    Point edge = CTX.getShapeFactory().pointXY(180, 5);
    double d = CTX.getDistCalc().distance(band.getCenter(), edge);
    assertEquals("edge point is 175 degrees from the center", 175, d, 1e-9);
    double[] s = centerAndEdgeScores(PointVectorStrategy.newInstance(CTX, "pv"), band, edge);
    assertEquals("score at the center", 1.0, s[0], 1e-6);
    // documented: zScaling = 0.1 * 175 = 17.5 -> 17.5 / (175 + 17.5) = 0.0909
    assertEquals("score at the furthest edge (documented ~0.1)", 0.10, s[1], 0.09);
  }

  /** The method is final in SpatialStrategy, so every strategy that has a distance source is affected. */
  public void testEveryStrategyAndA340DegreeBox() throws IOException {
    List<SpatialStrategy> strategies =
        List.of(
            PointVectorStrategy.newInstance(CTX, "pv"),
            BBoxStrategy.newInstance(CTX, "bbox"),
            new SerializedDVStrategy(CTX, "ser"),
            new RecursivePrefixTreeStrategy(new QuadPrefixTree(CTX, 25), "rpt"));
    // the band of test 1, and a box 340 degrees wide whose furthest boundary point (170, 0) is 170 degrees away
    Shape[] queries = {
      CTX.getShapeFactory().rect(-180, 180, -5, 5), CTX.getShapeFactory().rect(-170, 170, -10, 10)
    };
    Point[] edges = {CTX.getShapeFactory().pointXY(180, 5), CTX.getShapeFactory().pointXY(170, 0)};
    StringBuilder failures = new StringBuilder();
    for (SpatialStrategy strategy : strategies) {
      for (int i = 0; i < queries.length; i++) {
        double[] s = centerAndEdgeScores(strategy, queries[i], edges[i]);
        if (Math.abs(s[0] - 1.0) > 0.01 || Math.abs(s[1] - 0.10) > 0.09) {
          failures.append(
              String.format(
                  java.util.Locale.ROOT,
                  "%s query=%s center=%.5f edge(%s)=%.5f (documented ~0.1)%n",
                  strategy.getClass().getSimpleName(),
                  queries[i],
                  s[0],
                  edges[i],
                  s[1]));
        }
      }
    }
    assertTrue("scores outside the documented range:\n" + failures, failures.length() == 0);
  }

  /**
   * Side effect a user sees: with FunctionScoreQuery.boostByValue the too-small zScaling over-weights distance. Doc
   * "far" (inner score 10, 170 degrees from the center) and doc "near" (inner score 1, 10 degrees away), band query.
   * Documented zScaling 17.5: far = 10 * 17.5 / 187.5 = 0.933 > near = 17.5 / 27.5 = 0.636. With the zScaling the
   * code computes (0.5): far = 0.029 < near = 0.048, so the order flips.
   */
  public void testBoostByRecipDistanceOrder() throws IOException {
    SpatialStrategy strategy = PointVectorStrategy.newInstance(CTX, "pv");
    Rectangle band = CTX.getShapeFactory().rect(-180, 180, -5, 5);
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        for (Object[] row : new Object[][] {{"far", 170.0}, {"near", 10.0}}) {
          Document doc = new Document();
          doc.add(new StringField("id", (String) row[0], Field.Store.YES));
          for (Field f : strategy.createIndexableFields(CTX.getShapeFactory().pointXY((Double) row[1], 0))) {
            doc.add(f);
          }
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        Query inner =
            new BooleanQuery.Builder()
                .add(
                    new BoostQuery(new ConstantScoreQuery(new TermQuery(new Term("id", "far"))), 10f),
                    BooleanClause.Occur.SHOULD)
                .add(new ConstantScoreQuery(new TermQuery(new Term("id", "near"))), BooleanClause.Occur.SHOULD)
                .build();
        DoubleValuesSource recip = strategy.makeRecipDistanceValueSource(band);
        TopDocs top = searcher.search(FunctionScoreQuery.boostByValue(inner, recip), 2);
        String first = searcher.storedFields().document(top.scoreDocs[0].doc).get("id");
        assertEquals(
            "top hit with the documented zScaling (scores: "
                + top.scoreDocs[0].score
                + ", "
                + top.scoreDocs[1].score
                + ")",
            "far",
            first);
      }
    }
  }
}
