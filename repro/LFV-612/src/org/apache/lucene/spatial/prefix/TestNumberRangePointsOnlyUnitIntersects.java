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
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.spatial.prefix.tree.DateRangePrefixTree;
import org.apache.lucene.spatial.query.SpatialArgs;
import org.apache.lucene.spatial.query.SpatialOperation;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.shape.Shape;
import org.locationtech.spatial4j.shape.SpatialRelation;

/**
 * NumberRangePrefixTreeStrategy with pointsOnly=true: an Intersects query whose shape is a single
 * date unit coarser than full precision (a year, a month, a day, ...) matches nothing, although
 * the indexed point lies inside it and IsWithin with the same shape finds it.
 */
public class TestNumberRangePointsOnlyUnitIntersects extends LuceneTestCase {

  private static final String INDEXED = "2014-10-23T21:22:33.159"; // full (millisecond) precision

  private static final String[] QUERIES = {
    "2014", "2014-10", "[2014-10-01 TO 2014-10-31]", "2014-10-23", "2014-10-23T21:22:33"
  };

  private List<String> run(boolean pointsOnly) throws Exception {
    DateRangePrefixTree tree = new DateRangePrefixTree(DateRangePrefixTree.DEFAULT_CAL);
    NumberRangePrefixTreeStrategy strategy = new NumberRangePrefixTreeStrategy(tree, "date");
    strategy.setPointsOnly(pointsOnly);
    Shape indexed = tree.parseShape(INDEXED);
    List<String> wrong = new ArrayList<>();
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        Document doc = new Document();
        for (Field f : strategy.createIndexableFields(indexed)) {
          doc.add(f);
        }
        w.addDocument(doc);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        for (String q : QUERIES) {
          Shape queryShape = tree.parseShape(q);
          // the indexed point lies inside the query shape
          assertEquals(SpatialRelation.WITHIN, indexed.relate(queryShape));
          int within =
              searcher.count(
                  strategy.makeQuery(new SpatialArgs(SpatialOperation.IsWithin, queryShape)));
          int intersects =
              searcher.count(
                  strategy.makeQuery(new SpatialArgs(SpatialOperation.Intersects, queryShape)));
          if (within != 1 || intersects != 1) {
            wrong.add(q + " (shape " + queryShape + "): IsWithin=" + within + " Intersects=" + intersects);
          }
        }
      }
    }
    return wrong;
  }

  /** Control: the default (non-pointsOnly) configuration finds the document for every query. */
  public void testDefaultConfigurationMatches() throws Exception {
    assertEquals(List.of(), run(false));
  }

  /** pointsOnly=true: every Intersects query below should find the one indexed document. */
  public void testPointsOnlyIntersectsCoarserUnit() throws Exception {
    List<String> wrong = run(true);
    assertTrue("pointsOnly=true, indexed " + INDEXED + ", wrong counts: " + wrong, wrong.isEmpty());
  }
}
