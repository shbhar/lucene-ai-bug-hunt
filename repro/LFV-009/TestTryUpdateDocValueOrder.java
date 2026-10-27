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
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * IndexWriter#tryUpdateDocValue loses against an EARLIER updateDocValues(Term, ...) on the same
 * document while that term update is still buffered.
 *
 * <p>Contract (IndexWriter class javadoc): "Each method that changes the index returns a long
 * sequence number, which expresses the effective order in which each change was applied." The
 * tryUpdateDocValue call below succeeds and returns a larger sequence number than the term update,
 * so its value must be the one that survives.
 *
 * <p>Uses public API only. The writer uses a default IndexWriterConfig and an in-memory directory.
 */
public class TestTryUpdateDocValueOrder extends LuceneTestCase {

  private static Document doc(String id) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.NO));
    d.add(new NumericDocValuesField("n", 1));
    return d;
  }

  /** Value of field n for doc 0 of a single-segment reader. */
  private static long valueOfDoc0(IndexReader r) throws Exception {
    assertEquals(1, r.leaves().size());
    NumericDocValues n = r.leaves().get(0).reader().getNumericDocValues("n");
    assertTrue(n.advanceExact(0));
    return n.longValue();
  }

  /** Bug: term update n=2 (buffered), then tryUpdateDocValue n=3. Expected: n=3 wins. */
  public void testTryUpdateAfterBufferedTermUpdate() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      w.addDocument(doc("1")); // doc 0
      w.addDocument(doc("2")); // doc 1, a bystander
      try (DirectoryReader r = DirectoryReader.open(w)) { // flushes one segment; doc 0 is visible
        long seqTerm = w.updateDocValues(new Term("id", "1"), new NumericDocValuesField("n", 2));
        long seqTry = w.tryUpdateDocValue(r, 0, new NumericDocValuesField("n", 3));
        System.out.println("updateDocValues seq=" + seqTerm + ", tryUpdateDocValue seq=" + seqTry);
        assertTrue("tryUpdateDocValue must succeed", seqTry > 0);
        assertTrue("tryUpdateDocValue is the later operation", seqTry > seqTerm);

        long nrt;
        try (DirectoryReader fresh = DirectoryReader.open(w)) {
          nrt = valueOfDoc0(fresh);
        }
        w.commit();
        long committed;
        try (DirectoryReader c = DirectoryReader.open(dir)) {
          committed = valueOfDoc0(c);
        }
        TestUtil.checkIndex(dir); // the commit is structurally clean; only the value is wrong
        assertEquals(
            "the later tryUpdateDocValue (n=3) must win",
            "nrt n=3, commit n=3",
            "nrt n=" + nrt + ", commit n=" + committed);
      }
    }
  }

  /**
   * Bug, soft-deletes variant: soft-delete by term (buffered), then un-delete with
   * tryUpdateDocValue(null), which its javadoc names as the way to un-delete. Expected: doc is
   * live.
   */
  public void testTryUndeleteAfterBufferedSoftDelete() throws Exception {
    IndexWriterConfig iwc = new IndexWriterConfig().setSoftDeletesField("soft");
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      w.addDocument(doc("1")); // doc 0
      w.addDocument(doc("2")); // doc 1 keeps the segment from being 100% deleted
      try (DirectoryReader r = DirectoryReader.open(w)) {
        long seqDelete =
            w.updateDocValues(new Term("id", "1"), new NumericDocValuesField("soft", 1));
        long seqUndelete =
            w.tryUpdateDocValue(r, 0, new NumericDocValuesField("soft", (Long) null));
        System.out.println("soft delete seq=" + seqDelete + ", un-delete seq=" + seqUndelete);
        assertTrue("tryUpdateDocValue must succeed", seqUndelete > 0);
        assertTrue("un-delete is the later operation", seqUndelete > seqDelete);

        int nrt;
        try (DirectoryReader fresh = DirectoryReader.open(w)) {
          nrt = fresh.numDocs();
        }
        w.commit();
        int committed;
        try (DirectoryReader c =
            new SoftDeletesDirectoryReaderWrapper(DirectoryReader.open(dir), "soft")) {
          committed = c.numDocs();
        }
        assertEquals(
            "the later un-delete must win: both docs live",
            "nrt numDocs=2, commit numDocs=2",
            "nrt numDocs=" + nrt + ", commit numDocs=" + committed);
      }
    }
  }

  /** Control (passes): a flush between the two calls resolves the term update first. */
  public void testControlFlushBetween() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      w.addDocument(doc("1"));
      w.addDocument(doc("2"));
      try (DirectoryReader r = DirectoryReader.open(w)) {
        w.updateDocValues(new Term("id", "1"), new NumericDocValuesField("n", 2));
        w.flush();
        assertTrue(w.tryUpdateDocValue(r, 0, new NumericDocValuesField("n", 3)) > 0);
        try (DirectoryReader fresh = DirectoryReader.open(w)) {
          assertEquals(3, valueOfDoc0(fresh));
        }
      }
    }
  }
}
