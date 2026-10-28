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
package org.apache.lucene.spatial.v1check;

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.spatial.prefix.NumberRangePrefixTreeStrategy;
import org.apache.lucene.spatial.prefix.tree.DateRangePrefixTree;
import org.apache.lucene.spatial.prefix.tree.NumberRangePrefixTree.UnitNRShape;
import org.apache.lucene.spatial.query.SpatialArgs;
import org.apache.lucene.spatial.query.SpatialOperation;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.shape.Shape;
import org.locationtech.spatial4j.shape.SpatialRelation;

/**
 * Independent check (public API only, different package from the strategy): a
 * NumberRangePrefixTreeStrategy over a DateRangePrefixTree with pointsOnly=true indexes
 * full-precision dates; Intersects queries with coarser date units must match the dates inside the
 * unit. Oracle: ISO-8601 string prefix / lexicographic bounds, independent of the prefix tree.
 */
public class TestPointsOnlyDateUnitIntersects extends LuceneTestCase {

  // all full precision (millisecond) dates, 4-digit positive years, so ISO string order = time order
  private static final String[] DOCS = {
    "2014-10-23T21:22:33.159",
    "2014-10-05T08:00:00.001",
    "2014-03-01T12:00:00.500",
    "2013-12-31T23:59:59.999",
    "2015-01-01T00:00:00.001",
  };

  // query units coarser than the tree's maximum level
  private static final String[] COARSE_UNIT_QUERIES = {
    "2014",
    "2014-10",
    "2014-10-23",
    "2014-10-23T21",
    "2014-10-23T21:22",
    "2014-10-23T21:22:33",
    "2013-12-31T23:59:59",
    "2015",
    "[2014-10-01 TO 2014-10-31]",
  };

  // queries that do not go through the coarse-unit TermQuery path
  private static final String[] CONTROL_QUERIES = {
    "2014-10-23T21:22:33.159", // full-precision unit (a point)
    "[2014 TO 2015]", // a span, not a single unit
    "[2014-10-05 TO 2014-10-23]",
    "2016", // matches nothing
  };

  private DateRangePrefixTree tree;
  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;
  private NumberRangePrefixTreeStrategy pointsOnly; // used to index and to query
  private NumberRangePrefixTreeStrategy general; // same field, pointsOnly=false (query only)

  @Override
  public void setUp() throws Exception {
    super.setUp();
    tree = new DateRangePrefixTree(DateRangePrefixTree.DEFAULT_CAL);
    pointsOnly = new NumberRangePrefixTreeStrategy(tree, "when");
    pointsOnly.setPointsOnly(true);
    general = new NumberRangePrefixTreeStrategy(tree, "when");
    assertFalse(general.isPointsOnly());

    dir = newDirectory();
    IndexWriter w = new IndexWriter(dir, newIndexWriterConfig());
    for (String d : DOCS) {
      Shape s = tree.parseShape(d);
      assertTrue("precondition: indexed date is a unit " + d, s instanceof UnitNRShape);
      assertEquals(
          "precondition: indexed date is full precision " + d,
          tree.getMaxLevels(),
          ((UnitNRShape) s).getLevel());
      Document doc = new Document();
      for (Field f : pointsOnly.createIndexableFields(s)) {
        doc.add(f);
      }
      w.addDocument(doc);
    }
    w.close();
    reader = DirectoryReader.open(dir);
    searcher = new IndexSearcher(reader);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  /** Independent oracle: number of DOCS inside the query (unit = prefix, range = bounds). */
  private static int expected(String q) {
    int n = 0;
    for (String d : DOCS) {
      boolean in;
      if (q.startsWith("[")) {
        String[] parts = q.substring(1, q.length() - 1).split(" TO ");
        String lo = parts[0];
        String hi = parts[1];
        in =
            d.substring(0, lo.length()).compareTo(lo) >= 0
                && d.substring(0, hi.length()).compareTo(hi) <= 0;
      } else {
        in = d.startsWith(q);
      }
      if (in) n++;
    }
    return n;
  }

  private int count(NumberRangePrefixTreeStrategy s, SpatialOperation op, String q)
      throws Exception {
    return searcher.count(s.makeQuery(new SpatialArgs(op, tree.parseShape(q))));
  }

  /** Controls: oracle agrees with the paths that do not use the coarse-unit TermQuery. */
  public void testControls() throws Exception {
    List<String> bad = new ArrayList<>();
    List<String> all = new ArrayList<>(List.of(COARSE_UNIT_QUERIES));
    all.addAll(List.of(CONTROL_QUERIES));
    for (String q : all) {
      int exp = expected(q);
      int withinPO = count(pointsOnly, SpatialOperation.IsWithin, q);
      int intersectsGeneral = count(general, SpatialOperation.Intersects, q);
      if (withinPO != exp || intersectsGeneral != exp) {
        bad.add(
            q + ": expected=" + exp + " IsWithin(pointsOnly)=" + withinPO
                + " Intersects(pointsOnly=false)=" + intersectsGeneral);
      }
    }
    for (String q : CONTROL_QUERIES) {
      int exp = expected(q);
      int got = count(pointsOnly, SpatialOperation.Intersects, q);
      if (got != exp) {
        bad.add(q + ": expected=" + exp + " Intersects(pointsOnly)=" + got);
      }
    }
    assertTrue("control mismatches: " + bad, bad.isEmpty());
  }

  /** The claim: Intersects with a coarser unit and pointsOnly=true must match the docs inside. */
  public void testPointsOnlyIntersectsCoarseUnit() throws Exception {
    List<String> bad = new ArrayList<>();
    for (String q : COARSE_UNIT_QUERIES) {
      Shape qs = tree.parseShape(q);
      int exp = expected(q);
      // the tree itself agrees that each expected doc lies within the query shape
      int treeWithin = 0;
      for (String d : DOCS) {
        if (tree.parseShape(d).relate(qs) == SpatialRelation.WITHIN) treeWithin++;
      }
      assertEquals("oracle vs tree relate for " + q, exp, treeWithin);
      assertTrue("oracle must expect hits for " + q, exp > 0);
      Query query = pointsOnly.makeQuery(new SpatialArgs(SpatialOperation.Intersects, qs));
      int got = searcher.count(query);
      if (got != exp) {
        String lvl =
            qs instanceof UnitNRShape
                ? ("unit level " + ((UnitNRShape) qs).getLevel() + "/" + tree.getMaxLevels())
                : qs.getClass().getSimpleName();
        bad.add(
            q + " [" + lvl + ", query=" + query.getClass().getSimpleName() + "]: expected="
                + exp + " got=" + got);
      }
    }
    assertTrue(
        "pointsOnly=true Intersects with a coarser unit lost matches: " + bad, bad.isEmpty());
  }
}
