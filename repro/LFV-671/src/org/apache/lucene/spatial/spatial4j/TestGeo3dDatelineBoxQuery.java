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
package org.apache.lucene.spatial.spatial4j;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.spatial.query.SpatialArgs;
import org.apache.lucene.spatial.query.SpatialOperation;
import org.apache.lucene.spatial.vector.PointVectorStrategy;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.ShapeFactory;

public class TestGeo3dDatelineBoxQuery extends LuceneTestCase {

  public void testPointInsideDatelineBox() throws Exception {
    SpatialContext ctx = new Geo3dSpatialContextFactory().newSpatialContext();
    ShapeFactory shapes = ctx.getShapeFactory();
    PointVectorStrategy strategy = PointVectorStrategy.newInstance(ctx, "pv");
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        for (Field f : strategy.createIndexableFields(shapes.pointXY(175, 0))) {
          doc.add(f);
        }
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        SpatialArgs args =
            new SpatialArgs(SpatialOperation.Intersects, shapes.rect(170, -170, -10, 10));
        // expected: 1 hit, the point at longitude 175 is inside the box 170..-170; actual: 0
        assertEquals(1, new IndexSearcher(reader).count(strategy.makeQuery(args)));
      }
    }
  }
}
