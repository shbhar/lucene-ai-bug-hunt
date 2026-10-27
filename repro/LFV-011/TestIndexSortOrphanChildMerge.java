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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.Bits;

/**
 * With index sorting and a parent field, deleting only the parent document of a block (an ordinary
 * {@code deleteDocuments} or {@code updateDocuments} call) must not make later merges fail.
 *
 * <p>Contract: {@link IndexWriterConfig#setParentField(String)} and MIGRATE.md ("Document blocks
 * are maintained alongside their parent documents during sort and merge") describe merging of
 * blocks under index sorting; {@link IndexWriter#addDocuments(Iterable)} says only that search
 * features such as block joins "will not work as expected" when block documents are deleted. Delete
 * and update calls accept any term. Nothing documents that a merge may throw, which closes the
 * writer (a merge exception is a tragic event) and drops uncommitted documents.
 *
 * <p>The tests assert only that merging works, that the writer stays open and that documents
 * outside the broken block survive. They do not assert what happens to the orphaned child, so a
 * fix that cascades the parent delete to the children also passes.
 *
 * <p>Public API only. Deterministic: production codec, file-system directory, LogDocMergePolicy
 * and SerialMergeScheduler (merges run in the calling thread); no randomized components.
 */
public class TestIndexSortOrphanChildMerge extends LuceneTestCase {

  private static final String PARENT = "parent";

  /** Index sort on a long field; a merge policy that only merges when asked to. */
  private static IndexWriterConfig config() {
    LogDocMergePolicy mp = new LogDocMergePolicy();
    mp.setMergeFactor(1000);
    return config(mp);
  }

  private static IndexWriterConfig config(MergePolicy mp) {
    return new IndexWriterConfig()
        .setCodec(TestUtil.getDefaultCodec())
        .setIndexSort(new Sort(new SortField("sort", SortField.Type.LONG)))
        .setParentField(PARENT)
        .setMergePolicy(mp)
        .setMergeScheduler(new SerialMergeScheduler());
  }

  private static Document doc(String id, long sortValue) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    d.add(new NumericDocValuesField("sort", sortValue));
    return d;
  }

  private Directory newDir() throws IOException {
    return FSDirectory.open(createTempDir("orphan"));
  }

  /** Runs forceMerge(1) and turns any exception into a test failure that says what happened. */
  private static void forceMergeShouldWork(IndexWriter w, String step) {
    try {
      w.forceMerge(1);
    } catch (Throwable t) {
      throw new AssertionError(
          step
              + ": forceMerge(1) threw "
              + t
              + " (writer isOpen="
              + w.isOpen()
              + ", tragic exception="
              + w.getTragicException()
              + ")",
          t);
    }
  }

  private static Set<String> liveIds(DirectoryReader r) throws IOException {
    Set<String> ids = new HashSet<>();
    for (LeafReaderContext ctx : r.leaves()) {
      LeafReader leaf = ctx.reader();
      Bits live = leaf.getLiveDocs();
      StoredFields stored = leaf.storedFields();
      for (int d = 0; d < leaf.maxDoc(); d++) {
        if (live == null || live.get(d)) {
          ids.add(stored.document(d).get("id"));
        }
      }
    }
    return ids;
  }

  /**
   * Block [c1, p1]; delete only p1; add x. The first merge orders x (5) before c1, which sorts by
   * the value of its deleted parent (10), and drops p1, so the merged segment ends with c1, a child
   * with no parent after it. The next merge of that segment must still work.
   */
  public void testMergeAfterDeletingOnlyTheParent() throws Exception {
    try (Directory dir = newDir();
        IndexWriter w = new IndexWriter(dir, config())) {
      w.addDocuments(List.of(doc("c1", 50), doc("p1", 10))); // block: child, then parent
      w.commit();
      w.deleteDocuments(new Term("id", "p1")); // delete only the parent
      w.addDocument(doc("x", 5));
      w.commit();
      forceMergeShouldWork(w, "first merge"); // works: one segment [x, c1]
      w.addDocument(doc("y", 7));
      w.commit();
      w.addDocument(doc("z", 8)); // not committed
      forceMergeShouldWork(w, "second merge");
      assertTrue("writer must stay open", w.isOpen());
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        Set<String> ids = liveIds(r);
        assertTrue("x, y and the uncommitted z must survive, got " + ids,
            ids.containsAll(Set.of("x", "y", "z")));
      }
    }
  }

  /**
   * The state is durable: a committed index whose segment ends with such a child cannot be merged
   * by a new IndexWriter either. Merging it must work.
   */
  public void testNewWriterCanMergeCommittedIndex() throws Exception {
    try (Directory dir = newDir()) {
      try (IndexWriter w = new IndexWriter(dir, config())) {
        w.addDocuments(List.of(doc("c1", 50), doc("p1", 10)));
        w.commit();
        w.deleteDocuments(new Term("id", "p1"));
        w.addDocument(doc("x", 5));
        w.commit();
        forceMergeShouldWork(w, "first merge");
        w.addDocument(doc("y", 7));
        w.commit();
      } // closed cleanly; the index is committed
      try (IndexWriter w = new IndexWriter(dir, config())) {
        forceMergeShouldWork(w, "merge in a new writer");
        assertTrue("writer must stay open", w.isOpen());
      }
    }
  }

  /**
   * The common block-update pattern: re-index a block with updateDocuments, using a term that only
   * the parent carries. The old child stays live and, after the first merge, is the last document
   * of the segment (its deleted parent had the largest sort value).
   */
  public void testUpdateDocumentsWithParentOnlyTerm() throws Exception {
    try (Directory dir = newDir();
        IndexWriter w = new IndexWriter(dir, config())) {
      w.addDocuments(List.of(doc("order-1-item", 0), doc("order-1", 100)));
      w.commit();
      w.updateDocuments(
          new Term("id", "order-1"), List.of(doc("order-1-item", 0), doc("order-1", 1)));
      w.commit();
      forceMergeShouldWork(w, "first merge");
      w.addDocument(doc("other", 2));
      w.commit();
      forceMergeShouldWork(w, "second merge");
      assertTrue("writer must stay open", w.isOpen());
      try (DirectoryReader r = DirectoryReader.open(w)) {
        Set<String> ids = liveIds(r);
        assertTrue("order-1 and other must survive, got " + ids,
            ids.containsAll(Set.of("order-1", "other")));
      }
    }
  }

  /**
   * No forceMerge at all: the merges that the merge policy picks after commits hit the same
   * problem. Adding documents and committing must keep working.
   */
  public void testNaturalMerges() throws Exception {
    LogDocMergePolicy mp = new LogDocMergePolicy();
    mp.setMergeFactor(2);
    mp.setMinMergeDocs(1);
    try (Directory dir = newDir();
        IndexWriter w = new IndexWriter(dir, config(mp))) {
      String step = "setup";
      try {
        w.addDocuments(List.of(doc("c1", 50), doc("p1", 10)));
        w.commit();
        w.deleteDocuments(new Term("id", "p1"));
        w.addDocument(doc("x", 5));
        step = "commit after adding x";
        w.commit(); // the merge policy merges the two segments: [x, c1]
        for (int i = 0; i < 4; i++) {
          w.addDocument(doc("n" + i, 20 + i));
          step = "commit after adding n" + i;
          w.commit();
        }
      } catch (Throwable t) {
        throw new AssertionError(
            step
                + " threw "
                + t
                + " (writer isOpen="
                + w.isOpen()
                + ", tragic exception="
                + w.getTragicException()
                + ")",
            t);
      }
      assertTrue("writer must stay open", w.isOpen());
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertTrue(liveIds(r).containsAll(Set.of("x", "n0", "n1", "n2", "n3")));
      }
    }
  }

  /** Control: deleting the whole block (child and parent) leaves no orphan; merges work. */
  public void testControlDeleteWholeBlock() throws Exception {
    try (Directory dir = newDir();
        IndexWriter w = new IndexWriter(dir, config())) {
      w.addDocuments(List.of(doc("c1", 50), doc("p1", 10)));
      w.commit();
      w.deleteDocuments(new Term("id", "c1"), new Term("id", "p1"));
      w.addDocument(doc("x", 5));
      w.commit();
      forceMergeShouldWork(w, "first merge");
      w.addDocument(doc("y", 7));
      w.commit();
      w.addDocument(doc("z", 8));
      forceMergeShouldWork(w, "second merge");
      assertTrue(w.isOpen());
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(Set.of("x", "y", "z"), liveIds(r));
      }
    }
  }
}
