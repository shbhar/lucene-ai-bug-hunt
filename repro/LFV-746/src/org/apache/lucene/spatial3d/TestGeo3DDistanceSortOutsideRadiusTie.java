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

import java.util.Arrays;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.spatial3d.geom.GeoPoint;
import org.apache.lucene.spatial3d.geom.PlanetModel;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestGeo3DDistanceSortOutsideRadiusTie extends LuceneTestCase {

  public void testTopNOutsideRadiusUsesSecondarySort() throws Exception {
    PlanetModel pm = PlanetModel.WGS84;
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (int i = 0; i < 5; i++) {
          Document doc = new Document();
          doc.add(new Geo3DDocValuesField("loc", new GeoPoint(pm, Math.toRadians(60), 0), pm));
          doc.add(new NumericDocValuesField("tie", i));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Sort sort =
            new Sort(
                Geo3DDocValuesField.newDistanceSort("loc", 0, 0, 1_000_000, pm),
                new SortField("tie", SortField.Type.LONG, true));
        TopDocs full = searcher.search(MatchAllDocsQuery.INSTANCE, 100, sort);
        TopDocs top2 = searcher.search(MatchAllDocsQuery.INSTANCE, 2, sort);
        // all 5 docs are outside the radius (distance +Infinity): expected [4, 3], actual [1, 0]
        assertEquals(docs(full, 2), docs(top2, 2));
      }
    }
  }

  private static String docs(TopDocs td, int n) {
    return Arrays.toString(Arrays.stream(td.scoreDocs).limit(n).mapToInt(sd -> sd.doc).toArray());
  }
}
