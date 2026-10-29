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

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.spatial3d.geom.GeoPoint;
import org.apache.lucene.spatial3d.geom.PlanetModel;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestGeo3DDistanceSortMultiValued extends LuceneTestCase {

  public void testTopTwoIsHeadOfFullSort() throws IOException {
    PlanetModel planet = PlanetModel.WGS84;
    Directory dir = new ByteBuffersDirectory();
    IndexWriter w =
        new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()));
    for (double[] latitudes : new double[][] {{1}, {2}, {9, 0.5}, {5}, {6}}) {
      Document doc = new Document();
      for (double lat : latitudes) {
        doc.add(new Geo3DDocValuesField("p", new GeoPoint(planet, Math.toRadians(lat), 0), planet));
      }
      w.addDocument(doc);
    }
    w.close();
    DirectoryReader reader = DirectoryReader.open(dir);
    IndexSearcher searcher = new IndexSearcher(reader);
    Sort sort = new Sort(Geo3DDocValuesField.newDistanceSort("p", 0, 0, 2_000_000, planet));

    TopDocs all = searcher.search(MatchAllDocsQuery.INSTANCE, 5, sort);
    TopDocs top2 = searcher.search(MatchAllDocsQuery.INSTANCE, 2, sort);
    // expected: doc 2, doc 0 as in the full sort; actual: doc 0, then doc 2 at doc 3's distance
    assertEquals(firstTwo(all), firstTwo(top2));
    reader.close();
    dir.close();
  }

  private static String firstTwo(TopDocs topDocs) {
    FieldDoc a = (FieldDoc) topDocs.scoreDocs[0];
    FieldDoc b = (FieldDoc) topDocs.scoreDocs[1];
    return "doc " + a.doc + " at " + a.fields[0] + " m, doc " + b.doc + " at " + b.fields[0] + " m";
  }
}
