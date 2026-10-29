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
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.spatial3d.geom.GeoPoint;
import org.apache.lucene.spatial3d.geom.PlanetModel;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestGeo3DDistanceSortCentreDocument extends LuceneTestCase {

  public void testDocumentAtCentreGetsFiniteDistance() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      GeoPoint centre = new GeoPoint(PlanetModel.WGS84, Math.toRadians(40), Math.toRadians(-74));
      doc.add(new Geo3DDocValuesField("point", centre, PlanetModel.WGS84));
      w.addDocument(doc);
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        SortField sortField =
            Geo3DDocValuesField.newDistanceSort("point", 40, -74, 1000, PlanetModel.WGS84);
        TopFieldDocs top =
            new IndexSearcher(reader).search(new MatchAllDocsQuery(), 1, new Sort(sortField));
        double distance = (Double) ((FieldDoc) top.scoreDocs[0]).fields[0];
        // expected: a finite distance (the document is at the centre); actual: +Infinity
        assertTrue("expected a finite distance near 0 m, but was " + distance, distance < 1000);
      }
    }
  }
}
