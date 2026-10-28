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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.spatial.prefix.NumberRangePrefixTreeStrategy;
import org.apache.lucene.spatial.prefix.tree.DateRangePrefixTree;
import org.apache.lucene.spatial.query.SpatialArgs;
import org.apache.lucene.spatial.query.SpatialOperation;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;
import org.apache.lucene.util.Version;
import org.locationtech.spatial4j.shape.Shape;
import org.locationtech.spatial4j.shape.SpatialRelation;

/**
 * Version-portable check (only APIs present from 6.6 to main): NumberRangePrefixTreeStrategy with
 * setPointsOnly(true), one document holding a full-precision date, and Intersects queries whose
 * shape is a coarser date unit. Each query's shape contains the indexed point, so every Intersects
 * query must count 1, as IsWithin does and as Intersects does with pointsOnly=false.
 *
 * <p>Exit 0: no wrong count (or, on releases where pointsOnly cannot index a date, the trigger is
 * unavailable; the log says so). Exit 1 (AssertionError): the bug reproduces.
 */
public class NrPointsOnlyUnitIntersectsHistory {

  private static final String INDEXED = "2014-10-23T21:22:33.159"; // full (millisecond) precision

  // coarser units (the trigger), then two controls: the full-precision point and a two-unit span
  private static final String[] UNIT_QUERIES = {
    "2014", "2014-10", "[2014-10-01 TO 2014-10-31]", "2014-10-23", "2014-10-23T21:22:33"
  };
  private static final String[] CONTROL_QUERIES = {"2014-10-23T21:22:33.159", "[2014 TO 2015]"};

  public static void main(String[] args) throws Exception {
    System.out.println("lucene Version.LATEST=" + Version.LATEST);
    List<String> controlWrong = new ArrayList<>();
    // control: default configuration (pointsOnly=false)
    Boolean ok = run(false, controlWrong, true);
    if (!controlWrong.isEmpty()) {
      throw new IllegalStateException("control (pointsOnly=false) failed: " + controlWrong);
    }
    List<String> wrong = new ArrayList<>();
    ok = run(true, wrong, false);
    if (ok == null) {
      System.out.println(
          "RESULT: TRIGGER-UNAVAILABLE (pointsOnly=true cannot index a date unit in this version)");
      return;
    }
    if (!wrong.isEmpty()) {
      throw new AssertionError(
          "RESULT: BUG pointsOnly=true, indexed " + INDEXED + ", wrong counts: " + wrong);
    }
    System.out.println("RESULT: NO-BUG (every pointsOnly Intersects query matched)");
  }

  /** Returns null if indexing is rejected, else TRUE; wrong counts go to {@code wrong}. */
  private static Boolean run(boolean pointsOnly, List<String> wrong, boolean controlRun)
      throws Exception {
    DateRangePrefixTree tree = new DateRangePrefixTree(DateRangePrefixTree.DEFAULT_CAL);
    NumberRangePrefixTreeStrategy strategy = new NumberRangePrefixTreeStrategy(tree, "date");
    strategy.setPointsOnly(pointsOnly);
    Shape indexed = tree.parseShape(INDEXED);
    Field[] fields;
    try {
      fields = strategy.createIndexableFields(indexed);
    } catch (RuntimeException e) {
      if (!pointsOnly) {
        throw e;
      }
      System.out.println(
          "pointsOnly=true: indexing " + INDEXED + " rejected: " + e.getClass().getName() + ": "
              + e.getMessage());
      return null;
    }
    Path tmp = Files.createTempDirectory("nrpointsonly");
    try (Directory dir = new NIOFSDirectory(tmp)) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        for (Field f : fields) {
          doc.add(f);
        }
        w.addDocument(doc);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        List<String> all = new ArrayList<>();
        for (String q : UNIT_QUERIES) {
          all.add(q);
        }
        for (String q : CONTROL_QUERIES) {
          all.add(q);
        }
        for (String q : all) {
          Shape queryShape = tree.parseShape(q);
          SpatialRelation rel = indexed.relate(queryShape);
          // the indexed point lies inside the query shape (equal shapes relate as CONTAINS)
          boolean inside =
              rel == SpatialRelation.WITHIN || (rel == SpatialRelation.CONTAINS && q.equals(INDEXED));
          if (!inside) {
            throw new IllegalStateException("oracle: " + INDEXED + " " + rel + " " + queryShape);
          }
          Query within = strategy.makeQuery(new SpatialArgs(SpatialOperation.IsWithin, queryShape));
          Query inter = strategy.makeQuery(new SpatialArgs(SpatialOperation.Intersects, queryShape));
          int nWithin = searcher.count(within);
          int nInter = searcher.count(inter);
          boolean control = !isUnitQuery(q);
          String line =
              "pointsOnly=" + pointsOnly + " query=" + q + " (shape " + queryShape + ")"
                  + (control ? " [control]" : "") + ": IsWithin=" + nWithin + " Intersects="
                  + nInter + " intersectsQuery=" + inter.getClass().getSimpleName();
          System.out.println(line);
          if (nWithin != 1 || nInter != 1) {
            if (control && !controlRun) {
              throw new IllegalStateException("control query failed: " + line);
            }
            wrong.add(q + ": IsWithin=" + nWithin + " Intersects=" + nInter
                + " (" + inter.getClass().getSimpleName() + ")");
          }
        }
      }
    }
    return Boolean.TRUE;
  }

  private static boolean isUnitQuery(String q) {
    for (String u : UNIT_QUERIES) {
      if (u.equals(q)) {
        return true;
      }
    }
    return false;
  }
}
