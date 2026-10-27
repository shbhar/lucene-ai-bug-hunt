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
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * With a soft-deletes field, a pending soft-field change (an un-delete done with {@link
 * IndexWriter#tryUpdateDocValue}, or a soft delete done with {@link IndexWriter#updateDocValues})
 * is not seen by later term-based operations until the doc-values update is written, for example by
 * a refresh, flush or commit. The three un-delete tests assert what the javadocs of
 * tryUpdateDocValue and of the later call describe. The last test asserts that the outcome does not
 * depend on a refresh, which no javadoc states for this case (see its comment). Only public API is
 * used.
 *
 * <p>The writer config is deliberately not randomized, so the only flush or refresh points are the
 * ones written in each test; the test framework still picks the directory and the codec.
 */
public class TestTryUndeleteVsBufferedOps extends LuceneTestCase {

  private static final String SOFT = "soft_delete";

  private static IndexWriterConfig config() {
    return new IndexWriterConfig().setSoftDeletesField(SOFT).setMergePolicy(NoMergePolicy.INSTANCE);
  }

  private static Document doc(String id) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    return d;
  }

  /** Number of live documents with this id. */
  private static int count(IndexReader reader, String id) throws IOException {
    return new IndexSearcher(reader).count(new TermQuery(new Term("id", id)));
  }

  /** Top-level doc ID of the document with this id, whether it is deleted or not. */
  private static int docIdIgnoringDeletes(IndexReader reader, String id) throws IOException {
    for (LeafReaderContext ctx : reader.leaves()) {
      PostingsEnum postings = ctx.reader().postings(new Term("id", id));
      if (postings != null && postings.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
        return ctx.docBase + postings.docID();
      }
    }
    throw new AssertionError("no document with id " + id);
  }

  /** The committed index, with soft deletes applied. */
  private static DirectoryReader openCommitted(Directory dir) throws IOException {
    return new SoftDeletesDirectoryReaderWrapper(DirectoryReader.open(dir), SOFT);
  }

  /** Un-delete x with tryUpdateDocValue (as its javadoc describes), then soft-delete it again. */
  public void testSoftDeleteAfterTryUndeleteIsApplied() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, config())) {
      w.addDocument(doc("x"));
      w.addDocument(doc("y"));
      w.commit();
      w.updateDocValues(new Term("id", "x"), new NumericDocValuesField(SOFT, 1));
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertEquals("x is soft-deleted", 0, count(r, "x"));
        long undelete =
            w.tryUpdateDocValue(
                r, docIdIgnoringDeletes(r, "x"), new NumericDocValuesField(SOFT, null));
        assertTrue("tryUpdateDocValue succeeded", undelete > 0);
        long delete = w.updateDocValues(new Term("id", "x"), new NumericDocValuesField(SOFT, 2));
        assertTrue("the soft delete comes after the un-delete", undelete < delete);
      }
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertEquals("the later soft delete of x must apply", 0, count(r, "x"));
      }
    }
  }

  /** Control: the same calls with an NRT reader opened between the un-delete and the delete. */
  public void testControlSoftDeleteAfterTryUndeleteAndRefresh() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, config())) {
      w.addDocument(doc("x"));
      w.addDocument(doc("y"));
      w.commit();
      w.updateDocValues(new Term("id", "x"), new NumericDocValuesField(SOFT, 1));
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertTrue(
            w.tryUpdateDocValue(
                    r, docIdIgnoringDeletes(r, "x"), new NumericDocValuesField(SOFT, null))
                > 0);
      }
      DirectoryReader.open(w).close(); // the only difference from the test above
      w.updateDocValues(new Term("id", "x"), new NumericDocValuesField(SOFT, 2));
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertEquals("the later soft delete of x applies", 0, count(r, "x"));
      }
    }
  }

  /** Un-delete x, then replace it with softUpdateDocument: exactly one live x must remain. */
  public void testSoftUpdateAfterTryUndeleteLeavesOneLiveCopy() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, config())) {
        w.addDocument(doc("x"));
        w.addDocument(doc("y"));
        w.commit();
        w.updateDocValues(new Term("id", "x"), new NumericDocValuesField(SOFT, 1));
        try (DirectoryReader r = DirectoryReader.open(w)) {
          assertTrue(
              w.tryUpdateDocValue(
                      r, docIdIgnoringDeletes(r, "x"), new NumericDocValuesField(SOFT, null))
                  > 0);
          w.softUpdateDocument(new Term("id", "x"), doc("x"), new NumericDocValuesField(SOFT, 2));
        }
        w.commit();
      }
      TestUtil.checkIndex(dir); // the committed index is consistent
      try (DirectoryReader r = openCommitted(dir)) {
        assertEquals("exactly one live x in the commit", 1, count(r, "x"));
      }
    }
  }

  /**
   * Segment {y, x} where x is soft-deleted. Un-delete x, then delete y: the segment is not empty (x
   * is live again), so x must survive.
   */
  public void testTryUndeleteSurvivesDeleteOfOtherDoc() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, config())) {
        w.addDocument(doc("y"));
        Document x = doc("x");
        x.add(new NumericDocValuesField(SOFT, 1)); // x is indexed soft-deleted
        w.addDocument(x);
        try (DirectoryReader r = DirectoryReader.open(w)) {
          assertEquals("x is soft-deleted", 0, count(r, "x"));
          long undelete =
              w.tryUpdateDocValue(
                  r, docIdIgnoringDeletes(r, "x"), new NumericDocValuesField(SOFT, null));
          assertTrue("tryUpdateDocValue succeeded", undelete > 0);
          long delete = w.deleteDocuments(new Term("id", "y"));
          assertTrue("the delete comes after the un-delete", undelete < delete);
        }
        w.commit();
      }
      TestUtil.checkIndex(dir); // the committed index is consistent
      try (DirectoryReader r = openCommitted(dir)) {
        assertEquals("y is deleted", 0, count(r, "y"));
        assertEquals("x must be live in the commit", 1, count(r, "x"));
      }
    }
  }

  /**
   * No un-delete: soft-delete x twice, with soft=1 and then soft=2. The committed soft value must
   * not depend on whether an NRT reader was opened in between (the IndexWriter class javadoc: a
   * flush "just moves the internal buffered state in IndexWriter into the index").
   */
  public void testSoftDeleteOutcomeDoesNotDependOnRefresh() throws Exception {
    assertEquals(
        "committed soft value of x, without and with an NRT reader opened in between",
        softValueAfterTwoSoftDeletes(false),
        softValueAfterTwoSoftDeletes(true));
  }

  private static long softValueAfterTwoSoftDeletes(boolean refreshInBetween) throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, config())) {
        w.addDocument(doc("x"));
        w.addDocument(doc("y"));
        w.flush();
        w.updateDocValues(new Term("id", "x"), new NumericDocValuesField(SOFT, 1));
        if (refreshInBetween) {
          DirectoryReader.open(w).close();
        }
        w.updateDocValues(new Term("id", "x"), new NumericDocValuesField(SOFT, 2));
        w.commit();
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        for (LeafReaderContext ctx : r.leaves()) {
          PostingsEnum postings = ctx.reader().postings(new Term("id", "x"));
          if (postings == null || postings.nextDoc() == DocIdSetIterator.NO_MORE_DOCS) {
            continue;
          }
          NumericDocValues soft = ctx.reader().getNumericDocValues(SOFT);
          assertNotNull("x has a soft value", soft);
          assertTrue("x has a soft value", soft.advanceExact(postings.docID()));
          return soft.longValue();
        }
      }
      throw new AssertionError("no document with id x");
    }
  }
}
