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
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.FieldExistsQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * IndexWriter.deleteDocuments(Query...) and updateDocuments(Query, ...) accept any query and
 * return a sequence number. The query is evaluated only later, when the buffered delete packet is
 * applied (FrozenBufferedUpdates.applyQueryDeletes). If evaluating it throws -- for queries that
 * Lucene itself documents as failing on such a field, for example a PhraseQuery on a field indexed
 * without positions -- the exception is handled as a tragic event: the writer closes itself and
 * every document that was acknowledged but not yet committed is lost.
 *
 * <p>Expected: the writer rejects the query at call time, or the apply treats it as matching
 * nothing; either way the writer stays open and the acknowledged documents can be committed. The
 * IndexWriter class javadoc names Errors and checkpoint disasters as the cases in which it closes
 * itself; an IllegalStateException/IllegalArgumentException from the caller's own delete query is
 * neither. Public API only.
 */
public class TestDeleteQueryApplyFailureClosesWriter extends LuceneTestCase {

  private static Document doc(int i) {
    Document d = new Document();
    d.add(new StringField("id", "d" + i, Field.Store.NO)); // indexed DOCS only, no norms
    d.add(new LongPoint("lp", i));
    return d;
  }

  /** 6 committed docs, 4 acknowledged but uncommitted docs, then the delete, then commit(). */
  private void assertDeleteDoesNotCloseWriter(Query query) throws IOException {
    try (Directory dir = newDirectory()) {
      IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
      for (int i = 0; i < 6; i++) {
        w.addDocument(doc(i));
      }
      w.commit();
      for (int i = 6; i < 10; i++) {
        w.addDocument(doc(i)); // acknowledged: each call returned a sequence number
      }
      boolean rejectedAtCall = false;
      try {
        w.deleteDocuments(query);
      } catch (IllegalArgumentException | IllegalStateException e) {
        rejectedAtCall = true; // acceptable: the writer refused the query up front
      }
      Throwable commitFailure = null;
      try {
        w.commit();
      } catch (Throwable t) {
        commitFailure = t;
      }
      Throwable tragic = w.getTragicException();
      boolean open = w.isOpen();
      if (open) {
        w.close();
      }
      int committed;
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        committed = r.numDocs();
      }
      assertNull(
          "deleteDocuments("
              + query
              + ") returned normally (rejectedAtCall="
              + rejectedAtCall
              + "), but the next commit() failed and the writer closed itself: tragic="
              + tragic
              + ", committed docs="
              + committed
              + " of 10 acknowledged",
          commitFailure);
      assertTrue("writer must stay open", open);
      assertEquals("all acknowledged documents must be committed", 10, committed);
    }
  }

  /** PhraseQuery documents "IllegalStateException if positions are not indexed" at search time. */
  public void testPhraseQueryOnDocsOnlyField() throws Exception {
    assertDeleteDoesNotCloseWriter(new PhraseQuery("id", "d1", "d2"));
  }

  /** FieldExistsQuery on a StringField (no norms, no doc values, no vectors). */
  public void testFieldExistsOnStringField() throws Exception {
    assertDeleteDoesNotCloseWriter(new FieldExistsQuery("id"));
  }

  /** An int point query on a long point field (bytesPerDim 4 vs 8). */
  public void testIntPointQueryOnLongPointField() throws Exception {
    assertDeleteDoesNotCloseWriter(IntPoint.newRangeQuery("lp", 1, 3));
  }

  /**
   * The failure surfaces in an unrelated call: with maxBufferedDocs=2 the next addDocument flushes
   * the delete packet and throws the query's exception, and the writer is closed.
   */
  public void testUnrelatedAddDocumentFailsAndWriterCloses() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setMaxBufferedDocs(2));
      for (int i = 0; i < 6; i++) {
        w.addDocument(doc(i));
      }
      w.commit();
      for (int i = 6; i < 10; i++) {
        w.addDocument(doc(i));
      }
      w.deleteDocuments(new PhraseQuery("id", "d1", "d2")); // returns a sequence number
      Throwable addFailure = null;
      try {
        for (int i = 10; i < 14; i++) {
          w.addDocument(doc(i));
        }
      } catch (Throwable t) {
        addFailure = t;
      }
      Throwable tragic = w.getTragicException();
      boolean open = w.isOpen();
      if (open) {
        w.close();
      }
      assertNull(
          "an addDocument after the delete failed with the delete query's exception; writer open="
              + open
              + ", tragic="
              + tragic,
          addFailure);
      assertTrue(open);
    }
  }

  /**
   * updateDocuments(Query, docs): the atomic update is acknowledged, then the next commit closes
   * the writer, losing the update's own new document together with the other buffered documents.
   */
  public void testUpdateDocumentsByQuery() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
      for (int i = 0; i < 6; i++) {
        w.addDocument(doc(i));
      }
      w.commit();
      w.addDocument(doc(6));
      w.updateDocuments(new PhraseQuery("id", "d1", "d2"), List.of(doc(100)));
      Throwable commitFailure = null;
      try {
        w.commit();
      } catch (Throwable t) {
        commitFailure = t;
      }
      boolean open = w.isOpen();
      Throwable tragic = w.getTragicException();
      if (open) {
        w.close();
      }
      int committed;
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        committed = r.numDocs();
      }
      assertNull(
          "updateDocuments(Query, docs) returned normally, then commit() failed: open="
              + open
              + ", tragic="
              + tragic
              + ", committed="
              + committed
              + " of 8",
          commitFailure);
      assertEquals(8, committed);
    }
  }
}
