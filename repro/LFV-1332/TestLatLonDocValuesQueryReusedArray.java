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
package org.apache.lucene.document;

import org.apache.lucene.geo.LatLonGeometry;
import org.apache.lucene.geo.Rectangle;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestLatLonDocValuesQueryReusedArray extends LuceneTestCase {

  public void testReusedGeometryArray() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (double lon : new double[] {0, 10}) {
        Document doc = new Document();
        doc.add(new LatLonDocValuesField("loc", 0, lon));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setQueryCache(null);
        LatLonGeometry[] buf = {new Rectangle(-1, 1, -1, 1)};
        Query q0 =
            LatLonDocValuesField.newSlowGeometryQuery("loc", ShapeField.QueryRelation.WITHIN, buf);
        buf[0] = new Rectangle(-1, 1, 9, 11);
        Query q1 =
            LatLonDocValuesField.newSlowGeometryQuery("loc", ShapeField.QueryRelation.WITHIN, buf);
        Query or = new BooleanQuery.Builder().add(q0, Occur.SHOULD).add(q1, Occur.SHOULD).build();
        // expected 2 (one doc per square), actual 1: q0.equals(q1), so BooleanQuery merges them
        assertEquals(2, searcher.count(or));
      }
    }
  }
}
