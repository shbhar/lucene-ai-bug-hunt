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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * Edge cases around a soft-field change made with tryUpdateDocValue, for checking a fix that
 * applies such a change to the live docs right away: the soft-delete counts written to the commit
 * stay consistent (CheckIndex, a reopened writer), a merge right after an un-delete keeps the doc,
 * and a later term-based update treats a document soft-deleted by tryUpdateDocValue the same way
 * whether or not a reader was opened in between.
 */
public class TestTrySoftUpdateEdgeCases extends LuceneTestCase {

  private static final String SOFT = "soft_delete";

  private static Document doc(String id) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    d.add(new NumericDocValuesField("n", 1));
    return d;
  }

  private static int count(IndexReader reader, String id) throws IOException {
    return new IndexSearcher(reader).count(new TermQuery(new Term("id", id)));
  }

  private static int docIdIgnoringDeletes(IndexReader reader, String id) throws IOException {
    for (LeafReaderContext ctx : reader.leaves()) {
      PostingsEnum postings = ctx.reader().postings(new Term("id", id));
      if (postings != null && postings.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
        return ctx.docBase + postings.docID();
      }
    }
    throw new AssertionError("no document with id " + id);
  }

  private static long valueOf(IndexReader reader, String field, String id) throws IOException {
    for (LeafReaderContext ctx : reader.leaves()) {
      PostingsEnum postings = ctx.reader().postings(new Term("id", id));
      if (postings != null && postings.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
        NumericDocValues v = ctx.reader().getNumericDocValues(field);
        if (v == null || v.advanceExact(postings.docID()) == false) {
          return -1;
        }
        return v.longValue();
      }
    }
    throw new AssertionError("no document with id " + id);
  }

  private static int totalSoftDelCount(Directory dir) throws IOException {
    int total = 0;
    for (SegmentCommitInfo info : SegmentInfos.readLatestCommit(dir)) {
      total += info.getSoftDelCount();
    }
    return total;
  }

  /** Un-delete, commit, reopen the writer: the committed soft-delete count is 0 and stays so. */
  public void testUndeleteCommitReopenCounts() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig().setSoftDeletesField(SOFT).setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        w.addDocument(doc("x"));
        w.addDocument(doc("y"));
        w.commit();
        w.updateDocValues(new Term("id", "x"), new NumericDocValuesField(SOFT, 1));
        try (DirectoryReader r = DirectoryReader.open(w)) {
          assertEquals(0, count(r, "x"));
          assertTrue(
              w.tryUpdateDocValue(
                      r, docIdIgnoringDeletes(r, "x"), new NumericDocValuesField(SOFT, null))
                  > 0);
        }
        w.commit();
      }
      TestUtil.checkIndex(dir);
      assertEquals(0, totalSoftDelCount(dir));
      try (IndexWriter w =
          new IndexWriter(
              dir,
              new IndexWriterConfig()
                  .setSoftDeletesField(SOFT)
                  .setMergePolicy(NoMergePolicy.INSTANCE))) {
        try (DirectoryReader r = DirectoryReader.open(w)) {
          assertEquals(1, count(r, "x"));
          assertEquals(2, r.numDocs());
        }
      }
      TestUtil.checkIndex(dir);
    }
  }

  /** Soft-delete with tryUpdateDocValue, commit, reopen: the committed soft-delete count is 1. */
  public void testTrySoftDeleteCommitReopenCounts() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig().setSoftDeletesField(SOFT).setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        w.addDocument(doc("x"));
        w.addDocument(doc("y"));
        try (DirectoryReader r = DirectoryReader.open(w)) {
          assertTrue(
              w.tryUpdateDocValue(
                      r, docIdIgnoringDeletes(r, "x"), new NumericDocValuesField(SOFT, 1))
                  > 0);
        }
        w.commit();
      }
      TestUtil.checkIndex(dir);
      assertEquals(1, totalSoftDelCount(dir));
      try (DirectoryReader r =
          new SoftDeletesDirectoryReaderWrapper(DirectoryReader.open(dir), SOFT)) {
        assertEquals(0, count(r, "x"));
        assertEquals(1, r.numDocs());
      }
    }
  }

  /**
   * Un-delete, then force-merge before any reader is opened: the merged segment keeps x live. (A
   * log merge policy, so that no merge reclaims the soft-deleted x before the un-delete.)
   */
  public void testUndeleteThenForceMerge() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig().setSoftDeletesField(SOFT).setMergePolicy(new LogDocMergePolicy());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        w.addDocument(doc("x"));
        w.addDocument(doc("y"));
        w.commit();
        w.addDocument(doc("z"));
        w.commit(); // two segments
        w.updateDocValues(new Term("id", "x"), new NumericDocValuesField(SOFT, 1));
        try (DirectoryReader r = DirectoryReader.open(w)) {
          assertEquals(0, count(r, "x"));
          assertTrue(
              w.tryUpdateDocValue(
                      r, docIdIgnoringDeletes(r, "x"), new NumericDocValuesField(SOFT, null))
                  > 0);
        }
        w.forceMerge(1);
        w.commit();
        try (DirectoryReader r = DirectoryReader.open(w)) {
          assertEquals(1, r.leaves().size());
          assertEquals(1, count(r, "x"));
          assertEquals(3, r.numDocs());
        }
      }
      TestUtil.checkIndex(dir);
    }
  }

  /**
   * Un-delete under a retention merge policy that keeps every soft-deleted doc, then soft-delete
   * again by term and merge: x ends soft-deleted, retained, with the later soft value.
   */
  public void testUndeleteRedeleteRetentionMerge() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setSoftDeletesField(SOFT)
              .setMergePolicy(
                  new SoftDeletesRetentionMergePolicy(
                      SOFT, MatchAllDocsQuery::new, new LogDocMergePolicy()));
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        w.addDocument(doc("x"));
        w.addDocument(doc("y"));
        w.commit();
        w.addDocument(doc("z"));
        w.commit();
        w.updateDocValues(new Term("id", "x"), new NumericDocValuesField(SOFT, 1));
        try (DirectoryReader r = DirectoryReader.open(w)) {
          assertTrue(
              w.tryUpdateDocValue(
                      r, docIdIgnoringDeletes(r, "x"), new NumericDocValuesField(SOFT, null))
                  > 0);
        }
        w.updateDocValues(new Term("id", "x"), new NumericDocValuesField(SOFT, 2));
        w.forceMerge(1);
        w.commit();
        try (DirectoryReader r = DirectoryReader.open(w)) {
          assertEquals(0, count(r, "x"));
          assertEquals(3, r.maxDoc()); // x is retained
          assertEquals(2, valueOf(r, SOFT, "x"));
        }
      }
      TestUtil.checkIndex(dir);
    }
  }

  /**
   * Soft-delete x with tryUpdateDocValue, then update its numeric field by term. The outcome must
   * not depend on whether an NRT reader was opened in between.
   */
  public void testTermUpdateAfterTrySoftDeleteDoesNotDependOnRefresh() throws Exception {
    assertEquals(
        "n of the soft-deleted x, without and with an NRT reader opened in between",
        nAfterTrySoftDeleteThenTermUpdate(false),
        nAfterTrySoftDeleteThenTermUpdate(true));
  }

  private static long nAfterTrySoftDeleteThenTermUpdate(boolean refresh) throws IOException {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setSoftDeletesField(SOFT)
              .setMergePolicy(
                  new SoftDeletesRetentionMergePolicy(
                      SOFT, MatchAllDocsQuery::new, NoMergePolicy.INSTANCE));
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        w.addDocument(doc("x"));
        w.addDocument(doc("y"));
        try (DirectoryReader r = DirectoryReader.open(w)) {
          assertTrue(
              w.tryUpdateDocValue(
                      r, docIdIgnoringDeletes(r, "x"), new NumericDocValuesField(SOFT, 1))
                  > 0);
        }
        if (refresh) {
          DirectoryReader.open(w).close();
        }
        w.updateNumericDocValue(new Term("id", "x"), "n", 42);
        w.commit();
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        return valueOf(r, "n", "x");
      }
    }
  }
}
