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
package org.apache.lucene.spatial.prefix.tree;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.spatial.prefix.RecursivePrefixTreeStrategy;
import org.apache.lucene.spatial.query.SpatialArgs;
import org.apache.lucene.spatial.query.SpatialOperation;
import org.apache.lucene.spatial.spatial4j.Geo3dSpatialContextFactory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Point;

/**
 * S2PrefixTree: a point on an S2 cell edge (here the equator, an edge of every cell of the
 * equatorial faces) is indexed under exactly one cell (S2CellId.fromLatLng), but at query time
 * S2PrefixTreeCell.getNextLevelCells(point) returns every cell that touches the point. A Contains
 * query then demands that the indexed shape cover all of those cells, so a document is not found
 * to contain the very point it was indexed with. GeohashPrefixTree / QuadPrefixTree return 1.
 */
public class TestS2PointOnCellEdgeContains extends LuceneTestCase {

  private int count(SpatialPrefixTree grid, Point indexed, SpatialOperation op, Point query)
      throws IOException {
    RecursivePrefixTreeStrategy strategy = new RecursivePrefixTreeStrategy(grid, "s2");
    try (Directory dir = newDirectory()) {
      try (IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig())) {
        Document doc = new Document();
        for (Field f : strategy.createIndexableFields(indexed)) {
          doc.add(f);
        }
        writer.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        return new IndexSearcher(reader).count(strategy.makeQuery(new SpatialArgs(op, query)));
      }
    }
  }

  public void testPointOnEquatorContainsItself() throws IOException {
    SpatialContext ctx = new Geo3dSpatialContextFactory().newSpatialContext();
    Point p = ctx.getShapeFactory().pointXY(10, 0);
    assertTrue(SpatialOperation.Contains.evaluate(p, p));
    SpatialPrefixTree grid = new S2PrefixTree(ctx, 8);
    assertEquals(1, count(grid, p, SpatialOperation.Intersects, p)); // found by Intersects
    assertEquals(1, count(grid, p, SpatialOperation.Contains, p)); // but not by Contains
  }
}
