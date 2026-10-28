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
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * IndexWriter#tryUpdateDocValue and #tryDeleteDocument take a document ID relative to the given
 * NRT reader. They do not check that the ID is in [0, reader.maxDoc()): the composite-reader branch
 * of tryModifyDocument only asserts it, and the doc-values update buffer only asserts doc &lt;
 * maxDoc. The same reader rejects such an ID everywhere else (for example storedFields().document
 * throws IllegalArgumentException "docID must be &gt;= 0 and &lt; maxDoc").
 *
 * <p>With assertions disabled (production) an out-of-range ID is accepted, the call returns a
 * sequence number &gt; 0 ("the update succeeds"), and the update is applied to the bits that fit
 * the segment's doc-id width: it lands on a different, live document, or it is written to the
 * index as a doc-values entry for a document that does not exist; such a commit passes CheckIndex,
 * and the next merge shifts the doc values of the documents that follow it.
 *
 * <p>Each test expects the call to be rejected (any exception, or -1) and no document to change.
 * Uses only public IndexWriter / DirectoryReader API.
 */
public class TestTryUpdateDocValueOutOfRangeDocId extends LuceneTestCase {

  private static Document doc(String id, Long dv) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    if (dv != null) {
      d.add(new NumericDocValuesField("dv", dv));
    }
    return d;
  }

  private static IndexWriterConfig config() {
    // one segment per flush, no background merges, so the doc ids below are deterministic
    return new IndexWriterConfig()
        .setCodec(TestUtil.getDefaultCodec())
        .setMergePolicy(NoMergePolicy.INSTANCE)
        .setMergeScheduler(new SerialMergeScheduler())
        .setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH)
        .setRAMBufferSizeMB(64);
  }

  /** Reads "id=value" for every live document of the field, in doc-id order. */
  private static String dvByStoredId(DirectoryReader r, String field) throws IOException {
    StringBuilder sb = new StringBuilder();
    for (LeafReaderContext ctx : r.leaves()) {
      NumericDocValues dv = ctx.reader().getNumericDocValues(field);
      StoredFields sf = ctx.reader().storedFields();
      for (int doc = 0; doc < ctx.reader().maxDoc(); doc++) {
        String v = (dv != null && dv.advanceExact(doc)) ? Long.toString(dv.longValue()) : "-";
        sb.append(sf.document(doc).get("id")).append('=').append(v).append(' ');
      }
    }
    return sb.toString().trim();
  }

  /** Doc ids that the field's doc-values iterator returns at or beyond maxDoc, per leaf. */
  private static String dvDocsBeyondMaxDoc(DirectoryReader r, String field) throws IOException {
    StringBuilder sb = new StringBuilder();
    for (LeafReaderContext ctx : r.leaves()) {
      NumericDocValues dv = ctx.reader().getNumericDocValues(field);
      if (dv == null) {
        continue;
      }
      for (int doc = dv.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = dv.nextDoc()) {
        if (doc >= ctx.reader().maxDoc()) {
          sb.append(" doc ").append(doc).append(" (maxDoc ").append(ctx.reader().maxDoc());
          sb.append(", value ").append(dv.longValue()).append(')');
        }
      }
    }
    return sb.toString();
  }

  private static long tryUpdate(IndexWriter w, IndexReader r, int docID, Field f) {
    try {
      return w.tryUpdateDocValue(r, docID, f);
    } catch (Throwable t) { // rejected: fine (any exception)
      return Long.MIN_VALUE;
    }
  }

  /**
   * One segment of 8 documents (doc-id width 3 bits): doc id 8 is outside the reader, but the
   * update is applied to doc 0, a different live document.
   */
  public void testOutOfRangeIdUpdatesADifferentDocument() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, config())) {
      for (int i = 0; i < 8; i++) {
        w.addDocument(doc("k" + i, (long) i));
      }
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertEquals(8, r.maxDoc());
        String before = dvByStoredId(r, "dv");
        long seqNo = tryUpdate(w, r, r.maxDoc(), new NumericDocValuesField("dv", 777));
        w.commit();
        try (DirectoryReader after = DirectoryReader.open(dir)) {
          assertEquals(
              "tryUpdateDocValue(reader, maxDoc=" + r.maxDoc() + ") returned " + seqNo
                  + " and changed an existing document",
              before,
              dvByStoredId(after, "dv"));
        }
      }
    }
  }

  /**
   * One segment of 10 documents (doc-id width 4 bits), a sparse field: doc id 12 is accepted and
   * committed as a doc-values entry of a nonexistent document; after a merge with the next segment,
   * every document of that segment has the value of its predecessor.
   */
  public void testOutOfRangeIdCorruptsDocValuesOfTheNextSegmentOnMerge() throws Exception {
    try (Directory dir = newDirectory()) {
      String beyond;
      String msg;
      IndexWriterConfig iwc = config().setMergePolicy(new LogDocMergePolicy());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < 10; i++) {
          w.addDocument(doc("a" + i, i % 2 == 0 ? (long) i : null));
        }
        w.commit();
        long seqNo;
        try (DirectoryReader r = DirectoryReader.open(w)) {
          seqNo = tryUpdate(w, r, 12, new NumericDocValuesField("dv", 777));
        }
        w.commit();
        try (DirectoryReader c = DirectoryReader.open(dir)) {
          beyond = dvDocsBeyondMaxDoc(c, "dv");
        }
        msg = "tryUpdateDocValue(reader, 12) on a 10-document reader returned " + seqNo;
        for (int i = 0; i < 10; i++) {
          w.addDocument(doc("b" + i, 1000L + i));
        }
        w.forceMerge(1);
        w.commit();
      }
      try (DirectoryReader merged = DirectoryReader.open(dir)) {
        StringBuilder expected = new StringBuilder();
        for (int i = 0; i < 10; i++) {
          expected.append("a").append(i).append('=').append(i % 2 == 0 ? Long.toString(i) : "-");
          expected.append(' ');
        }
        for (int i = 0; i < 10; i++) {
          expected.append("b").append(i).append('=').append(1000 + i).append(' ');
        }
        assertEquals(
            msg,
            "committed entries beyond maxDoc: [] | after merge: " + expected.toString().trim(),
            "committed entries beyond maxDoc: [" + beyond.trim() + "] | after merge: "
                + dvByStoredId(merged, "dv"));
      }
    }
  }

  /** The soft-deletes variant: an out-of-range id soft-deletes a different, live document. */
  public void testOutOfRangeIdSoftDeletesADifferentDocument() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, config().setSoftDeletesField("soft"))) {
      for (int i = 0; i < 8; i++) {
        w.addDocument(doc("k" + i, (long) i));
      }
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(w)) {
        long seqNo = tryUpdate(w, r, 9, new NumericDocValuesField("soft", 1));
        try (DirectoryReader after = DirectoryReader.open(w)) {
          assertEquals(
              "tryUpdateDocValue(reader, 9, soft=1) on an 8-document reader returned " + seqNo
                  + " and soft-deleted an existing document",
              8,
              after.numDocs());
        }
      }
    }
  }
  /**
   * tryDeleteDocument: an out-of-range id is reported as a successful delete (sequence number &gt;
   * 0) although nothing was deleted.
   */
  public void testOutOfRangeIdTryDeleteReportsSuccess() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, config())) {
      for (int i = 0; i < 10; i++) {
        w.addDocument(doc("k" + i, (long) i));
      }
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(w)) {
        long seqNo;
        try {
          seqNo = w.tryDeleteDocument(r, r.maxDoc());
        } catch (Throwable t) { // rejected: fine (any exception)
          seqNo = Long.MIN_VALUE;
        }
        try (DirectoryReader after = DirectoryReader.open(w)) {
          assertFalse(
              "tryDeleteDocument(reader, maxDoc=" + r.maxDoc() + ") returned " + seqNo
                  + " (success) but numDocs is still " + after.numDocs(),
              seqNo > 0 && after.numDocs() == r.numDocs());
        }
      }
    }
  }
}
