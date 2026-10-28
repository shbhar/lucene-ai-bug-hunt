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
package org.apache.lucene.spatial.vector;

import java.io.IOException;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.DoubleValues;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.spatial.SpatialStrategy;
import org.apache.lucene.spatial.bbox.BBoxStrategy;
import org.apache.lucene.spatial.prefix.RecursivePrefixTreeStrategy;
import org.apache.lucene.spatial.prefix.tree.QuadPrefixTree;
import org.apache.lucene.spatial.serialized.SerializedDVStrategy;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.context.SpatialContextFactory;
import org.locationtech.spatial4j.shape.impl.RectangleImpl;

/**
 * In a non-geo (Cartesian) SpatialContext, every strategy's makeDistanceValueSource gives a document without a
 * shape the value Double.MAX_VALUE (DistanceToShapeValueSource, ShapeFieldCacheDistanceValueSource: {@code
 * ctx.isGeo() ? 180 * multiplier : Double.MAX_VALUE}), so an ascending distance sort lists such documents last.
 * PointVectorStrategy's DistanceValueSource had the same rule until LUCENE-7737 (7.0.0) and now always returns {@code
 * 180 * multiplier}.
 */
public class TestPointVectorMissingDistanceNonGeo extends LuceneTestCase {

  private static SpatialContext cartesian() {
    SpatialContextFactory factory = new SpatialContextFactory();
    factory.geo = false;
    factory.worldBounds = new RectangleImpl(-1000, 1000, -1000, 1000, null);
    return factory.newSpatialContext();
  }

  /** value for the document without a point, and the ids in ascending-distance order */
  private static Object[] run(SpatialStrategy strategy) throws IOException {
    SpatialContext ctx = strategy.getSpatialContext();
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        Document far = new Document();
        far.add(new StringField("id", "far", Field.Store.YES));
        for (Field f : strategy.createIndexableFields(ctx.getShapeFactory().pointXY(500, 0))) {
          far.add(f);
        }
        w.addDocument(far);
        Document none = new Document();
        none.add(new StringField("id", "none", Field.Store.YES));
        w.addDocument(none);
        w.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        DoubleValuesSource distance =
            strategy.makeDistanceValueSource(ctx.getShapeFactory().pointXY(0, 0), 1.0);
        DoubleValues v = distance.getValues(reader.leaves().get(0), null);
        assertTrue(v.advanceExact(1));
        double missing = v.doubleValue();
        IndexSearcher searcher = newSearcher(reader);
        TopDocs top = searcher.search(new MatchAllDocsQuery(), 2, new Sort(distance.getSortField(false)));
        String order =
            searcher.storedFields().document(top.scoreDocs[0].doc).get("id")
                + ","
                + searcher.storedFields().document(top.scoreDocs[1].doc).get("id");
        return new Object[] {missing, order};
      }
    }
  }

  /** The other strategies, as the reference for the same API on the same context. */
  public void testSiblingStrategiesPutMissingLast() throws IOException {
    SpatialContext ctx = cartesian();
    for (SpatialStrategy s :
        List.of(
            BBoxStrategy.newInstance(ctx, "bbox"),
            new SerializedDVStrategy(ctx, "ser"),
            new RecursivePrefixTreeStrategy(new QuadPrefixTree(ctx, 20), "rpt"))) {
      Object[] r = run(s);
      assertEquals(s.getClass().getSimpleName(), Double.MAX_VALUE, (double) r[0], 0.0);
      assertEquals(s.getClass().getSimpleName(), "far,none", r[1]);
    }
  }

  public void testPointVectorMissingValueNonGeo() throws IOException {
    Object[] r = run(PointVectorStrategy.newInstance(cartesian(), "pv"));
    assertEquals("ascending distance sort: the document without a point goes last", "far,none", r[1]);
    assertEquals("distance of a document without a point", Double.MAX_VALUE, (double) r[0], 0.0);
  }
}
