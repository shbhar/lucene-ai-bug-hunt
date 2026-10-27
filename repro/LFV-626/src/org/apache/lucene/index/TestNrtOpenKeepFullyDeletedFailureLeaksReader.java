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
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.tests.store.MockDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IOSupplier;

/**
 * When MergePolicy#keepFullyDeletedSegment throws (it is declared to throw IOException, and
 * SoftDeletesRetentionMergePolicy runs its retention query there) while IndexWriter opens a
 * near-real-time reader, StandardDirectoryReader.open(IndexWriter, ...) has already obtained the
 * segment's SegmentReader but neither keeps it in its list of readers (which it releases on
 * failure) nor decRefs it. The reader, and the segment's open files, stay open after
 * IndexWriter.close(); MockDirectoryWrapper.close() reports them.
 *
 * <p>Public API: IndexWriterConfig#setSoftDeletesField, SoftDeletesRetentionMergePolicy,
 * FilterMergePolicy; the injected IOException stands in for a read error of the retention query.
 */
public class TestNrtOpenKeepFullyDeletedFailureLeaksReader extends LuceneTestCase {

  /** Throws from keepFullyDeletedSegment once it is armed and called from an NRT reader open. */
  static final class FailingKeepPolicy extends FilterMergePolicy {
    volatile boolean armed;
    volatile int fired;

    FailingKeepPolicy(MergePolicy in) {
      super(in);
    }

    @Override
    public boolean keepFullyDeletedSegment(IOSupplier<CodecReader> readerIOSupplier)
        throws IOException {
      if (armed && callStackContains(StandardDirectoryReader.class, "open")) {
        armed = false;
        fired++;
        throw new IOException("simulated read error in the retention check");
      }
      return in.keepFullyDeletedSegment(readerIOSupplier);
    }
  }

  static Document doc(String id) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    return d;
  }

  public void testFailedKeepCheckDuringNrtOpenLeaksSegmentReader() throws Exception {
    MockDirectoryWrapper dir = newMockDirectory();
    FailingKeepPolicy mp =
        new FailingKeepPolicy(
            new SoftDeletesRetentionMergePolicy("soft", MatchAllDocsQuery::new, NoMergePolicy.INSTANCE));
    IndexWriterConfig iwc =
        new IndexWriterConfig(new StandardAnalyzer())
            .setSoftDeletesField("soft")
            .setMergePolicy(mp)
            .setMergeScheduler(new SerialMergeScheduler())
            .setMaxFullFlushMergeWaitMillis(0)
            .setCommitOnClose(false);
    IndexWriter w = new IndexWriter(dir, iwc);
    w.addDocument(doc("a"));
    w.commit(); // segment _0 with one document
    // soft-delete that document: _0 has no live document left, but the retention policy keeps it
    w.softUpdateDocument(new Term("id", "a"), doc("a"), new NumericDocValuesField("soft", 1));

    mp.armed = true;
    IOException e = expectThrows(IOException.class, () -> DirectoryReader.open(w));
    assertEquals("simulated read error in the retention check", e.getMessage());
    assertEquals(1, mp.fired);
    assertTrue("the failure is not tragic", w.isOpen());
    assertNull(w.getTragicException());

    // the writer keeps working; a later NRT reader opens fine
    try (DirectoryReader r = DirectoryReader.open(w)) {
      assertEquals(1, r.numDocs());
    }
    w.close();

    // MockDirectoryWrapper.close() throws if any file is still open
    RuntimeException leak = null;
    try {
      dir.close();
    } catch (RuntimeException re) {
      leak = re;
    }
    assertNull(
        "files still open after IndexWriter.close(): " + (leak == null ? "" : leak.getMessage()),
        leak);
  }
}
