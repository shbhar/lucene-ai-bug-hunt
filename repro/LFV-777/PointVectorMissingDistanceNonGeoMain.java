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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.DoubleValues;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.spatial.SpatialStrategy;
import org.apache.lucene.spatial.bbox.BBoxStrategy;
import org.apache.lucene.spatial.vector.PointVectorStrategy;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.context.SpatialContextFactory;
import org.locationtech.spatial4j.shape.impl.RectangleImpl;

/**
 * Version-portable main() repro for Lucene 7.0 and later (makeDistanceValueSource returns a
 * DoubleValuesSource). Only public API; no test framework, so it runs unchanged on 7.x, 8.x, 9.x,
 * 10.x and main.
 *
 * <p>Index: doc 0 has a point at (500, 0), doc 1 has no point. Query point (0, 0), multiplier 1.
 * In a non-geo (Cartesian) context, the expected distance for the document without a point is
 * Double.MAX_VALUE (the rule of the other makeDistanceValueSource implementations), so an
 * ascending distance sort gives "far,none". Exits non-zero (an uncaught AssertionError thrown
 * explicitly, so also under -da) when PointVectorStrategy gives the point-less document a smaller
 * value than the real one.
 */
public class PointVectorMissingDistanceNonGeoMain {

  static SpatialContext cartesian() {
    SpatialContextFactory factory = new SpatialContextFactory();
    factory.geo = false;
    factory.worldBounds = new RectangleImpl(-1000, 1000, -1000, 1000, null);
    return factory.newSpatialContext();
  }

  /** Returns {missing value, ascending order of doc ids as names}. */
  static Object[] run(SpatialStrategy strategy, double farX) throws Exception {
    SpatialContext ctx = strategy.getSpatialContext();
    Path path = Files.createTempDirectory("pvnull");
    try (Directory dir = FSDirectory.open(path)) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document far = new Document();
        far.add(new StringField("id", "far", Field.Store.NO));
        for (Field f : strategy.createIndexableFields(ctx.makePoint(farX, 0))) {
          far.add(f);
        }
        w.addDocument(far); // doc 0
        Document none = new Document();
        none.add(new StringField("id", "none", Field.Store.NO));
        w.addDocument(none); // doc 1
        w.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        if (reader.leaves().size() != 1 || reader.maxDoc() != 2) {
          throw new IllegalStateException("setup: expected one segment with two docs");
        }
        DoubleValuesSource distance =
            strategy.makeDistanceValueSource(ctx.makePoint(0, 0), 1.0);
        LeafReaderContext leaf = reader.leaves().get(0);
        DoubleValues v = distance.getValues(leaf, null);
        if (!v.advanceExact(1)) {
          throw new IllegalStateException("advanceExact(1) returned false");
        }
        double missing = v.doubleValue();
        DoubleValues v0 = distance.getValues(leaf, null);
        v0.advanceExact(0);
        double farValue = v0.doubleValue();
        IndexSearcher searcher = new IndexSearcher(reader);
        Sort sort = new Sort(distance.getSortField(false)).rewrite(searcher);
        TopDocs top = searcher.search(new MatchAllDocsQuery(), 2, sort);
        String order =
            (top.scoreDocs[0].doc == 0 ? "far" : "none")
                + ","
                + (top.scoreDocs[1].doc == 0 ? "far" : "none");
        return new Object[] {missing, order, farValue};
      }
    }
  }

  public static void main(String[] args) throws Exception {
    SpatialContext cart = cartesian();
    System.out.println("context geo=" + cart.isGeo() + " worldBounds=" + cart.getWorldBounds());

    Object[] bbox = run(BBoxStrategy.newInstance(cart, "bbox"), 500);
    System.out.println(
        "control BBoxStrategy nonGeo: missing=" + bbox[0] + " far=" + bbox[2] + " order=" + bbox[1]);

    Object[] geo = run(PointVectorStrategy.newInstance(SpatialContext.GEO, "pv"), 179);
    System.out.println(
        "control PointVectorStrategy GEO: missing=" + geo[0] + " far=" + geo[2] + " order=" + geo[1]);

    Object[] pv = run(PointVectorStrategy.newInstance(cart, "pv"), 500);
    System.out.println(
        "PointVectorStrategy nonGeo: missing=" + pv[0] + " far=" + pv[2] + " order=" + pv[1]);

    if (!"far,none".equals(bbox[1]) || (double) bbox[0] != Double.MAX_VALUE) {
      throw new IllegalStateException("CONTROL UNEXPECTED: BBoxStrategy non-geo " + bbox[0] + " " + bbox[1]);
    }
    if (!"far,none".equals(pv[1]) || (double) pv[0] != Double.MAX_VALUE) {
      throw new AssertionError(
          "BUG REPRODUCED: PointVectorStrategy non-geo missing distance = "
              + pv[0]
              + " (expected Double.MAX_VALUE), ascending order "
              + pv[1]
              + " (expected far,none)");
    }
    System.out.println("OK: PointVectorStrategy non-geo missing value is Double.MAX_VALUE");
  }
}
