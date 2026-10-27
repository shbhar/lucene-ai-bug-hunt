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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * IndexWriter#hasDeletions ("Returns true if this index has deletions (including buffered
 * deletions)") checks committed segments with SegmentCommitInfo#hasDeletions, which only knows hard
 * deletes. With a soft-deletes field, a writer whose reader pool does not hold the segment (for
 * example a writer opened on an existing index) answers false although getDocStats() and its own
 * NRT readers count the soft-deleted document as deleted.
 */
public class TestIndexWriterHasDeletionsSoftDeletes extends LuceneTestCase {

  private static IndexWriterConfig config() {
    return new IndexWriterConfig()
        .setSoftDeletesField("soft")
        .setMergePolicy(NoMergePolicy.INSTANCE);
  }

  private static Document doc(String id) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    return d;
  }

  private static void buildIndex(Directory dir, boolean hard) throws Exception {
    try (IndexWriter w = new IndexWriter(dir, config())) {
      w.addDocument(doc("a"));
      w.addDocument(doc("b"));
      w.commit();
      if (hard) {
        w.deleteDocuments(new Term("id", "a"));
      } else {
        w.softUpdateDocument(new Term("id", "a"), doc("a"), new NumericDocValuesField("soft", 1));
      }
      w.commit();
    }
  }

  /** The answer must not depend on whether an NRT reader was opened first. */
  public void testAnswerChangesOnceAnNrtReaderIsOpened() throws Exception {
    try (Directory dir = newDirectory()) {
      buildIndex(dir, false);
      try (IndexWriter w = new IndexWriter(dir, config())) {
        IndexWriter.DocStats stats = w.getDocStats();
        assertEquals(3, stats.maxDoc);
        assertEquals(2, stats.numDocs); // the writer itself counts one deleted document
        boolean beforeReader = w.hasDeletions();
        try (DirectoryReader r = DirectoryReader.open(w)) {
          assertEquals(1, r.numDeletedDocs());
        }
        boolean afterReader = w.hasDeletions(); // true: the segment is now pooled
        assertTrue(afterReader);
        assertEquals("hasDeletions() before vs after opening an NRT reader", afterReader, beforeReader);
      }
    }
  }

  public void testSoftDeletedDocumentFreshWriterBeforeAnyReader() throws Exception {
    try (Directory dir = newDirectory()) {
      buildIndex(dir, false);
      try (IndexWriter w = new IndexWriter(dir, config())) {
        assertEquals(2, w.getDocStats().numDocs);
        assertTrue("index has a (soft) deleted document", w.hasDeletions());
      }
    }
  }

  /** Control: the same with a hard delete. */
  public void testControlHardDeletedDocumentAfterWriterReopen() throws Exception {
    try (Directory dir = newDirectory()) {
      buildIndex(dir, true);
      try (IndexWriter w = new IndexWriter(dir, config())) {
        assertEquals(1, w.getDocStats().numDocs);
        assertTrue(w.hasDeletions());
      }
    }
  }
}
