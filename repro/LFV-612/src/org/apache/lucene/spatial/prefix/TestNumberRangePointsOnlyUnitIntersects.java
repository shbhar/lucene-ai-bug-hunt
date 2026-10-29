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
import org.apache.lucene.spatial.prefix.tree.DateRangePrefixTree;
import org.apache.lucene.spatial.query.SpatialArgs;
import org.apache.lucene.spatial.query.SpatialOperation;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestNumberRangePointsOnlyUnitIntersects extends LuceneTestCase {

  public void testPointsOnlyIntersectsMonth() throws Exception {
    DateRangePrefixTree tree = new DateRangePrefixTree(DateRangePrefixTree.DEFAULT_CAL);
    NumberRangePrefixTreeStrategy strategy = new NumberRangePrefixTreeStrategy(tree, "date");
    strategy.setPointsOnly(true);
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        for (Field f : strategy.createIndexableFields(tree.parseShape("2014-10-23T21:22:33.159"))) {
          doc.add(f);
        }
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        SpatialArgs args = new SpatialArgs(SpatialOperation.Intersects, tree.parseShape("2014-10"));
        // expected: the one document (its date lies in October 2014) matches; actual: 0 hits
        assertEquals(1, new IndexSearcher(reader).count(strategy.makeQuery(args)));
      }
    }
  }
}
