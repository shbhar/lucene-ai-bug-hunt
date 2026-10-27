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
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/** Edge cases for a fix of the nearest() total: sparse field, two segments, soft deletes. */
public class TestNearestTotalHitsEdges extends LuceneTestCase {

  private static Document doc(int id, boolean withPoint) {
    Document doc = new Document();
    doc.add(new StringField("id", Integer.toString(id), Field.Store.NO));
    if (withPoint) {
      doc.add(new LatLonPoint("point", 40.0 + id * 0.1, 50.0));
    }
    return doc;
  }

  private static TopFieldDocs nearest(DirectoryReader reader) throws IOException {
    return LatLonPoint.nearest(new IndexSearcher(reader), "point", 40.0, 50.0, 100);
  }

  private static void assertTotal(TopFieldDocs td, int liveWithPoint) {
    String state = "totalHits=" + td.totalHits + " liveWithPoint=" + liveWithPoint;
    assertEquals(state, liveWithPoint, td.scoreDocs.length);
    if (td.totalHits.relation() == TotalHits.Relation.EQUAL_TO) {
      assertEquals(state, liveWithPoint, td.totalHits.value());
    } else {
      assertTrue(state, td.totalHits.value() <= liveWithPoint);
    }
  }

  /** Docs 0-3 have a point, docs 4-5 do not; delete one of each kind in turn. */
  public void testSparseFieldWithDeletions() throws IOException {
    for (int deleted : new int[] {0, 4}) {
      IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
      try (Directory dir = newDirectory();
          IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < 6; i++) {
          w.addDocument(doc(i, i < 4));
        }
        w.commit();
        w.deleteDocuments(new Term("id", Integer.toString(deleted)));
        try (DirectoryReader reader = DirectoryReader.open(w)) {
          assertTotal(nearest(reader), deleted < 4 ? 3 : 4);
        }
      }
    }
  }

  /** A dense segment with a deletion plus a segment without deletions: the total stays exact. */
  public void testTwoSegmentsDenseField() throws IOException {
    IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < 4; i++) {
        w.addDocument(doc(i, true));
      }
      w.commit();
      for (int i = 4; i < 7; i++) {
        w.addDocument(doc(i, true));
      }
      w.commit();
      w.deleteDocuments(new Term("id", "1"));
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        assertEquals(2, reader.leaves().size());
        TopFieldDocs td = nearest(reader);
        assertTotal(td, 6);
        assertEquals(TotalHits.Relation.EQUAL_TO, td.totalHits.relation());
      }
    }
  }

  /** Soft deletes: the old copy of an updated document is hidden through the live docs. */
  public void testSoftUpdate() throws IOException {
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setMergePolicy(NoMergePolicy.INSTANCE)
            .setSoftDeletesField("soft_delete");
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < 3; i++) {
        w.addDocument(doc(i, true));
      }
      w.softUpdateDocument(
          new Term("id", "1"), doc(1, true), new NumericDocValuesField("soft_delete", 1));
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        assertEquals(4, reader.maxDoc());
        assertEquals(3, reader.numDocs());
        assertTotal(nearest(reader), 3);
      }
    }
  }
}
