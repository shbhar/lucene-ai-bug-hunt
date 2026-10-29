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
package org.apache.lucene.spatial3d;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.spatial3d.geom.GeoPoint;
import org.apache.lucene.spatial3d.geom.PlanetModel;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestGeo3DDistanceSortSearchAfter extends LuceneTestCase {

  public void testSearchAfterReturnsNextHit() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (int lat = 1; lat <= 2; lat++) {
          Document doc = new Document();
          doc.add(
              new Geo3DDocValuesField(
                  "p", new GeoPoint(PlanetModel.WGS84, Math.toRadians(lat), 0), PlanetModel.WGS84));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Sort sort =
            new Sort(Geo3DDocValuesField.newDistanceSort("p", 0, 0, 1_000_000, PlanetModel.WGS84));
        ScoreDoc last = searcher.search(MatchAllDocsQuery.INSTANCE, 1, sort).scoreDocs[0];
        ScoreDoc[] page2 =
            searcher.searchAfter(last, MatchAllDocsQuery.INSTANCE, 1, sort).scoreDocs;
        // expected page 2 = [doc 1] after the doc-0 cursor (in meters); actual: no hits
        assertEquals("hits on page 2", 1, page2.length);
        assertEquals(1, page2[0].doc);
      }
    }
  }
}
