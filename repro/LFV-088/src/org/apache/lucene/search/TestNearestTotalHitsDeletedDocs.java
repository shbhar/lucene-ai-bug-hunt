/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.search;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LatLonPoint;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * LatLonPoint#nearest reports TotalHits as the sum of PointValues#getDocCount, which ignores
 * deletions, with Relation.EQUAL_TO. With a deleted document the exact-looking total is larger than
 * the number of documents that can match (here: n is larger than every candidate, so all of them are
 * returned).
 */
public class TestNearestTotalHitsDeletedDocs extends LuceneTestCase {

  private void check(boolean delete) throws Exception {
    IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < 3; i++) {
        Document d = new Document();
        d.add(new StringField("id", Integer.toString(i), Field.Store.NO));
        d.add(new LatLonPoint("point", 40.0 + i, 50.0));
        w.addDocument(d);
      }
      w.commit();
      if (delete) {
        w.deleteDocuments(new Term("id", "0"));
      }
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        TopFieldDocs td = LatLonPoint.nearest(searcher, "point", 40.0, 50.0, 10);
        assertEquals(reader.numDocs(), td.scoreDocs.length); // every live doc has one point
        // TotalHits.toString() is "<value> hits" for EQUAL_TO and "<value>+ hits" for a lower bound
        String total = td.totalHits.toString();
        if (total.endsWith("+ hits") == false) {
          assertEquals("an EQUAL_TO total must be exact", td.scoreDocs.length + " hits", total);
        }
      }
    }
  }

  /** Bug: 2 live documents, TotalHits says exactly 3. */
  public void testTotalHitsWithDeletedDoc() throws Exception {
    check(true);
  }

  /** Control: no deletions. */
  public void testControlNoDeletions() throws Exception {
    check(false);
  }
}
