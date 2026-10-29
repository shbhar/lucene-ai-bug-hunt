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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.context.SpatialContextFactory;

public class TestPointVectorMissingDistanceNonGeo extends LuceneTestCase {

  public void testDocWithoutPointSortsLastInNonGeoContext() throws Exception {
    SpatialContextFactory factory = new SpatialContextFactory();
    factory.geo = false;
    SpatialContext ctx = factory.newSpatialContext();
    PointVectorStrategy strategy = PointVectorStrategy.newInstance(ctx, "pv");
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document withPoint = new Document();
      for (Field f : strategy.createIndexableFields(ctx.getShapeFactory().pointXY(500, 0))) {
        withPoint.add(f);
      }
      w.addDocument(withPoint); // doc 0
      w.addDocument(new Document()); // doc 1, no point
      try (DirectoryReader r = DirectoryReader.open(w)) {
        Sort byDistance =
            new Sort(
                strategy
                    .makeDistanceValueSource(ctx.getShapeFactory().pointXY(0, 0))
                    .getSortField(false));
        TopDocs top = new IndexSearcher(r).search(new MatchAllDocsQuery(), 2, byDistance);
        // expected: doc 0 (distance 500) first, as in the other strategies; actual: doc 1 (180)
        assertEquals(0, top.scoreDocs[0].doc);
      }
    }
  }
}
