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
package org.apache.lucene.index;

import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * IndexWriter rejects documents that contain the parent field ("reserved field") and rejects
 * doc-values updates of index-sort fields, but it accepts doc-values updates of the parent field
 * itself. Such an update moves block boundaries inside already-sorted segments; the next merge then
 * writes a segment that violates the index sort, or a segment that no later merge can read.
 */
public class TestParentFieldDocValuesUpdate extends LuceneTestCase {

  private static Document doc(String id, long sortValue) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    d.add(new NumericDocValuesField("sort", sortValue));
    return d;
  }

  private static IndexWriter newWriter(Directory dir) throws Exception {
    return new IndexWriter(
        dir,
        new IndexWriterConfig()
            .setCodec(TestUtil.getDefaultCodec())
            .setIndexSort(new Sort(new SortField("sort", SortField.Type.LONG)))
            .setParentField("_parent")
            .setMergePolicy(new LogDocMergePolicy())
            .setMergeScheduler(new SerialMergeScheduler()));
  }

  /** Index a block [child(sort=9), parent(sort=1)] and a single doc (sort=5) in two segments. */
  private static void indexTwoSegments(IndexWriter w) throws Exception {
    w.addDocuments(List.of(doc("child", 9), doc("parent", 1)));
    w.commit();
    w.addDocument(doc("single", 5));
    w.commit();
  }

  /** Marks the child of a block as a parent through the doc-values update API. */
  public void testUpdateMarksChildAsParent() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = newWriter(dir)) {
        indexTwoSegments(w);
        try {
          w.updateNumericDocValue(new Term("id", "child"), "_parent", -1);
        } catch (IllegalArgumentException expected) {
          return; // rejecting the update, like for index-sort fields, is fine
        }
        w.forceMerge(1);
      }
      // if the update is accepted, the merged segment must still be sorted by the index sort
      CheckIndex.Status status = TestUtil.checkIndex(dir);
      assertTrue(status.clean);
    }
  }

  /** Removes a parent's mark through the doc-values update API. */
  public void testUpdateRemovesParentMark() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = newWriter(dir)) {
        indexTwoSegments(w);
        try {
          w.updateDocValues(
              new Term("id", "parent"), new NumericDocValuesField("_parent", (Long) null));
        } catch (IllegalArgumentException expected) {
          return;
        }
        w.forceMerge(1);
        w.addDocument(doc("later", 0));
        w.commit();
        // if the update is accepted, later merges must keep working
        w.forceMerge(1);
      }
      assertTrue(TestUtil.checkIndex(dir).clean);
    }
  }
}
