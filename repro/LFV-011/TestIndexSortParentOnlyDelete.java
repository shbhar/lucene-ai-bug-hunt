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

import java.io.IOException;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * Index sorting with a parent field: deleting only the parent of a block (an ordinary delete or
 * update by a term that only the parent carries) must not make later merges fail. Public API only;
 * deterministic (default codec, file-system directory, merges only when asked, in this thread).
 */
public class TestIndexSortParentOnlyDelete extends LuceneTestCase {

  private static final Sort SORT = new Sort(new SortField("sort", SortField.Type.LONG));

  private static IndexWriterConfig config(Sort indexSort) {
    LogDocMergePolicy mp = new LogDocMergePolicy();
    mp.setMergeFactor(1000); // no natural merges: merge only on forceMerge
    IndexWriterConfig c =
        new IndexWriterConfig()
            .setCodec(TestUtil.getDefaultCodec())
            .setParentField("parent")
            .setMergePolicy(mp)
            .setMergeScheduler(new SerialMergeScheduler());
    return indexSort == null ? c : c.setIndexSort(indexSort);
  }

  private static Document doc(String id, long sortValue) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.NO));
    d.add(new NumericDocValuesField("sort", sortValue));
    return d;
  }

  private static void forceMergeMustWork(IndexWriter w) {
    try {
      w.forceMerge(1);
    } catch (Exception e) {
      throw new AssertionError("forceMerge(1) threw " + e + "; writer open: " + w.isOpen(), e);
    }
  }

  private static int count(Directory dir, String id) throws IOException {
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      return new IndexSearcher(r).count(new TermQuery(new Term("id", id)));
    }
  }

  /** Block [c1, p1]; delete only p1; merge, add a document, merge again. */
  public void testMergeAfterDeletingOnlyTheParent() throws Exception {
    try (Directory dir = FSDirectory.open(createTempDir());
        IndexWriter w = new IndexWriter(dir, config(SORT))) {
      w.addDocuments(List.of(doc("c1", 50), doc("p1", 10))); // a block: child, then parent
      w.commit();
      w.deleteDocuments(new Term("id", "p1")); // delete only the parent
      w.addDocument(doc("x", 5));
      w.commit();
      forceMergeMustWork(w); // works: one segment [x, c1], its last doc a child with no parent
      w.addDocument(doc("y", 7));
      w.commit();
      w.addDocument(doc("z", 8)); // not committed yet
      forceMergeMustWork(w);
      assertTrue("writer must stay open", w.isOpen());
      w.commit();
      assertEquals("the uncommitted document must survive", 1, count(dir, "z"));
    }
  }

  /** The common block-update pattern: re-index a block by a term that only the parent carries. */
  public void testUpdateBlockByParentOnlyTerm() throws Exception {
    try (Directory dir = FSDirectory.open(createTempDir());
        IndexWriter w = new IndexWriter(dir, config(SORT))) {
      w.addDocuments(List.of(doc("order-1-item", 0), doc("order-1", 100)));
      w.commit();
      w.updateDocuments(
          new Term("id", "order-1"), List.of(doc("order-1-item", 0), doc("order-1", 1)));
      w.commit();
      forceMergeMustWork(w); // works: the old item is now the last doc, with no parent after it
      w.addDocument(doc("other", 2));
      w.commit();
      forceMergeMustWork(w);
      assertTrue("writer must stay open", w.isOpen());
    }
  }

  /** The same lookup in Sorter, through SortingCodecReader, on an index without an index sort. */
  public void testSortingCodecReaderAfterDeletingOnlyTheParent() throws Exception {
    try (Directory dir = FSDirectory.open(createTempDir())) {
      try (IndexWriter w = new IndexWriter(dir, config(null))) {
        w.addDocument(doc("x", 5));
        w.addDocuments(List.of(doc("c1", 50), doc("p1", 10)));
        w.deleteDocuments(new Term("id", "p1"));
        w.commit();
        forceMergeMustWork(w); // one segment [x, c1]
        w.commit();
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        CodecReader leaf = (CodecReader) r.leaves().get(0).reader();
        assertEquals(2, SortingCodecReader.wrap(leaf, SORT).maxDoc());
      }
    }
  }

  /** Control: deleting the whole block leaves no orphan, and merges work. */
  public void testControlDeleteWholeBlock() throws Exception {
    try (Directory dir = FSDirectory.open(createTempDir());
        IndexWriter w = new IndexWriter(dir, config(SORT))) {
      w.addDocuments(List.of(doc("c1", 50), doc("p1", 10)));
      w.commit();
      w.deleteDocuments(new Term("id", "c1"), new Term("id", "p1"));
      w.addDocument(doc("x", 5));
      w.commit();
      forceMergeMustWork(w);
      w.addDocument(doc("y", 7));
      w.commit();
      w.addDocument(doc("z", 8));
      forceMergeMustWork(w);
      assertTrue(w.isOpen());
      w.commit();
      assertEquals(1, count(dir, "z"));
    }
  }
}
