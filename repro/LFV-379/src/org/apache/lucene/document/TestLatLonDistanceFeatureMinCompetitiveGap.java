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

import org.apache.lucene.geo.GeoEncodingUtils;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestLatLonDistanceFeatureMinCompetitiveGap extends LuceneTestCase {

  private static void addPoint(IndexWriter w, double lat, double lon) throws Exception {
    Document doc = new Document();
    doc.add(new LatLonPoint("loc", lat, lon));
    doc.add(new LatLonDocValuesField("loc", lat, lon));
    w.addDocument(doc);
  }

  public void testClosestDocReturned() throws Exception {
    int numFar = 2000;
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
        addPoint(w, GeoEncodingUtils.decodeLatitude(214586), 0);
        for (int i = 0; i < numFar; i++) {
          addPoint(w, 40 + i * 1e-3, 100);
        }
        addPoint(w, GeoEncodingUtils.decodeLatitude(214544), 0);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        TopDocs top =
            new IndexSearcher(reader)
                .search(LatLonPoint.newDistanceFeatureQuery("loc", 1, 0, 0, 1000), 1);
        // doc 0 is 999.999 m due north, the last doc 999.803 m: expected the last doc, actual doc 0
        assertEquals("top-1 doc", numFar + 1, top.scoreDocs[0].doc);
      }
    }
  }
}
