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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.spatial.prefix.tree.Cell;
import org.apache.lucene.spatial.prefix.tree.CellIterator;
import org.apache.lucene.spatial.prefix.tree.S2PrefixTree;
import org.apache.lucene.spatial.query.SpatialArgs;
import org.apache.lucene.spatial.query.SpatialOperation;
import org.apache.lucene.spatial.spatial4j.Geo3dSpatialContextFactory;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Point;

/**
 * Which coordinates are on S2 cell edges: for each point, index it alone with the default S2 tree
 * (arity 1, 31 levels), count Contains(p) and Intersects(p), and count how many children the query
 * side gets at the first level below the point's face. Prints one line per point; never fails.
 */
public class TestS2GridLinesProbe extends LuceneTestCase {

  public void testGridLines() throws Exception {
    SpatialContext ctx = new Geo3dSpatialContextFactory().newSpatialContext();
    S2PrefixTree grid = new S2PrefixTree(ctx, S2PrefixTree.getMaxLevels(1));
    RecursivePrefixTreeStrategy st = new RecursivePrefixTreeStrategy(grid, "geo");
    double[][] lonLat = {
      {10, 0}, {-120, 0}, {0, 10}, {0, -33}, {90, 10}, {-90, 10}, {180, 10}, {45, 20}, {-45, 20},
      {135, -20}, {-135, -20}, {0, 0}, {1, 10}, {46, 20}, {10, 0.001}, {91, 10}
    };
    for (double[] ll : lonLat) {
      Point p = ctx.getShapeFactory().pointXY(ll[0], ll[1]);
      int contains;
      int intersects;
      try (Directory dir = new ByteBuffersDirectory()) {
        try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
          Document d = new Document();
          for (Field f : st.createIndexableFields(p)) {
            d.add(f);
          }
          w.addDocument(d);
        }
        try (DirectoryReader r = DirectoryReader.open(dir)) {
          IndexSearcher s = new IndexSearcher(r);
          contains = s.count(st.makeQuery(new SpatialArgs(SpatialOperation.Contains, p)));
          intersects = s.count(st.makeQuery(new SpatialArgs(SpatialOperation.Intersects, p)));
        }
      }
      // children touching p, per level, along the indexing path (first 4 levels below the world)
      StringBuilder touching = new StringBuilder();
      CellIterator path = grid.getTreeCellIterator(p, 5);
      Cell cell = grid.getWorldCell();
      while (path.hasNext()) {
        Cell target = path.next();
        int n = 0;
        CellIterator kids = cell.getNextLevelCells(p);
        while (kids.hasNext()) {
          kids.next();
          n++;
        }
        touching.append(touching.length() == 0 ? "" : ",").append(n);
        cell = target;
        if (cell.getLevel() >= 4) {
          break;
        }
      }
      System.out.println(
          "LINE p=("
              + ll[0]
              + ","
              + ll[1]
              + ") contains="
              + contains
              + " intersects="
              + intersects
              + " touchingChildrenPerLevel(levels 1-4)="
              + touching);
    }
  }
}
