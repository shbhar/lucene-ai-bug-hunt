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
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IOSupplier;

/**
 * With a parent field configured, a document block that IndexWriter rejects at a child (here: an
 * immense term, a documented IllegalArgumentException) can be flushed as a segment of its own
 * whose field infos have no parent field, because the parent field is registered only when the
 * last document of a block is processed. That segment is fully deleted; if the merge policy keeps
 * fully deleted segments (MergePolicy#keepFullyDeletedSegment, which SoftDeletesRetentionMergePolicy
 * implements), every later merge of it with any other segment fails with "expected parent doc
 * field to be ... across all segments", which closes IndexWriter, and the same happens again after
 * the index is reopened.
 */
public class TestRejectedBlockSegmentWithoutParentField extends LuceneTestCase {

  private static Document doc(String id) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    return d;
  }

  /** A merge policy that keeps fully deleted segments (a documented MergePolicy hook). */
  private static MergePolicy keepFullyDeleted() {
    return new FilterMergePolicy(newLogMergePolicy()) {
      @Override
      public boolean keepFullyDeletedSegment(IOSupplier<CodecReader> readerIOSupplier) {
        return true;
      }
    };
  }

  private static MergePolicy softDeletesRetention() {
    return new SoftDeletesRetentionMergePolicy("_soft", MatchAllDocsQuery::new, newLogMergePolicy());
  }

  private static IndexWriterConfig config(MergePolicy mp) {
    return new IndexWriterConfig(new MockAnalyzer(random()))
        .setParentField("_parent")
        .setSoftDeletesField("_soft")
        .setMergePolicy(mp)
        .setMergeScheduler(new SerialMergeScheduler());
  }

  /** Builds: [doc a] [rejected block: 2 deleted docs, no parent field] [doc b], all committed. */
  private static IndexWriter buildIndex(Directory dir, MergePolicy mp) throws IOException {
    IndexWriter w = new IndexWriter(dir, config(mp));
    w.addDocument(doc("a"));
    w.commit();
    Document badChild = doc("child");
    badChild.add(
        new StringField("body", "x".repeat(IndexWriter.MAX_TERM_LENGTH + 1), Field.Store.NO));
    expectThrows(
        IllegalArgumentException.class, () -> w.addDocuments(List.of(doc("c0"), badChild, doc("p"))));
    assertTrue(w.isOpen());
    w.commit();
    w.addDocument(doc("b"));
    w.commit();
    assertNull(w.getTragicException());
    return w;
  }

  private static void forceMergeMustWork(IndexWriter w, String what) throws IOException {
    try {
      w.forceMerge(1);
    } catch (Throwable t) {
      fail(
          what
              + ": forceMerge(1) failed with "
              + t
              + "; writer open="
              + w.isOpen()
              + ", tragic exception="
              + w.getTragicException());
    }
    try (DirectoryReader r = DirectoryReader.open(w)) {
      assertEquals(2, r.numDocs());
    }
    w.close();
  }

  public void testSoftDeletesRetentionMergePolicy() throws Exception {
    try (Directory dir = newDirectory()) {
      forceMergeMustWork(buildIndex(dir, softDeletesRetention()), "SoftDeletesRetentionMergePolicy");
    }
  }

  public void testKeepFullyDeletedSegmentMergePolicy() throws Exception {
    try (Directory dir = newDirectory()) {
      forceMergeMustWork(buildIndex(dir, keepFullyDeleted()), "keepFullyDeletedSegment=true");
    }
  }

  /** The broken segment is committed, so a new writer on the same index fails the same way. */
  public void testFailureRecursAfterReopen() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriter w = buildIndex(dir, softDeletesRetention());
      try {
        w.forceMerge(1);
      } catch (Throwable expectedOnMain) {
        // first failure; the writer closed itself
      }
      if (w.isOpen()) {
        w.close();
      }
      IndexWriter w2 = new IndexWriter(dir, config(softDeletesRetention()));
      forceMergeMustWork(w2, "reopened writer");
    }
  }
}
