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

import java.io.IOException;
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

/** LatLonPoint#nearest must not count deleted documents in an EQUAL_TO total. */
public class TestNearestTotalHits extends LuceneTestCase {

  private static Document doc(int id, double lat) {
    Document doc = new Document();
    doc.add(new StringField("id", Integer.toString(id), Field.Store.NO));
    doc.add(new LatLonPoint("point", lat, 50.0));
    return doc;
  }

  /** Every live document has one point and n is larger than the index, so all of them are hits. */
  private static void assertTotal(DirectoryReader reader) throws IOException {
    TopFieldDocs td = LatLonPoint.nearest(new IndexSearcher(reader), "point", 40.0, 50.0, 100);
    String state =
        "maxDoc=" + reader.maxDoc() + " numDocs=" + reader.numDocs() + " totalHits=" + td.totalHits;
    assertEquals(state, reader.numDocs(), td.scoreDocs.length); // the hits are right
    if (td.totalHits.relation() == TotalHits.Relation.EQUAL_TO) {
      assertEquals("exact total; " + state, reader.numDocs(), td.totalHits.value());
    } else {
      assertTrue("lower bound; " + state, td.totalHits.value() <= reader.numDocs());
    }
  }

  /** Three documents, one deleted. NoMergePolicy only keeps the deleted document in place. */
  public void testDeletedDocument() throws IOException {
    IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < 3; i++) {
        w.addDocument(doc(i, 40.0 + i));
      }
      w.commit();
      w.deleteDocuments(new Term("id", "0"));
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        assertTotal(reader); // fails: 2 hits, totalHits "3 hits" (EQUAL_TO)
      }
    }
  }

  /** Default IndexWriterConfig: ten documents, one of them updated once. */
  public void testUpdatedDocument() throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (int i = 0; i < 10; i++) {
          w.addDocument(doc(i, 40.0 + i * 0.1));
        }
        w.commit();
        w.updateDocument(new Term("id", "3"), doc(3, 45.0));
        w.commit();
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertTrue("the old copy is not merged away yet", reader.hasDeletions());
        assertTotal(reader); // fails: 10 hits, totalHits "11 hits" (EQUAL_TO)
      }
    }
  }
}
