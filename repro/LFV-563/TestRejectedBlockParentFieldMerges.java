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
import java.util.Locale;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IOSupplier;

/**
 * A document block that IndexWriter rejects at a child must not leave a segment that makes later
 * merges fail.
 *
 * <p>With a parent field and no index sort, IndexWriter adds the parent field only when it indexes
 * the last document of a block. If a block is rejected at a child (here: a term longer than {@link
 * IndexWriter#MAX_TERM_LENGTH}) and is the only content of its in-memory segment, that segment is
 * flushed with no live documents and without the parent field. Merge policies normally drop such a
 * segment. A policy whose {@link MergePolicy#keepFullyDeletedSegment} returns true keeps it, for
 * example {@link SoftDeletesRetentionMergePolicy} with a retention query that matches the rejected
 * documents. A merge that combines it with segments holding no multi-document block then throws
 * "expected parent doc field ... across all segments", which closes IndexWriter. The segment is
 * committed, so a new writer fails the same way, and so does FieldInfos#getMergedFieldInfos on a
 * reader that holds it next to other segments.
 */
public class TestRejectedBlockParentFieldMerges extends LuceneTestCase {

  private static Document doc(String id) {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.YES));
    return doc;
  }

  private static IndexWriterConfig config(MergePolicy mergePolicy) {
    return new IndexWriterConfig(new MockAnalyzer(random()))
        .setParentField("_parent")
        .setSoftDeletesField("_soft")
        .setMergePolicy(mergePolicy)
        .setMergeScheduler(new SerialMergeScheduler());
  }

  /** Keeps every soft-deleted document, as an application that keeps change history would. */
  private static MergePolicy retainAll() {
    return new SoftDeletesRetentionMergePolicy(
        "_soft", MatchAllDocsQuery::new, new LogDocMergePolicy());
  }

  /** Any policy that keeps fully deleted segments; no document here is soft-deleted. */
  private static MergePolicy keepFullyDeleted() {
    return new FilterMergePolicy(new LogDocMergePolicy()) {
      @Override
      public boolean keepFullyDeletedSegment(IOSupplier<CodecReader> readerIOSupplier) {
        return true;
      }
    };
  }

  /** Commits three segments: [a] [the rejected block] [b]. */
  private static IndexWriter indexWithRejectedBlock(Directory dir, MergePolicy mergePolicy)
      throws IOException {
    IndexWriter w = new IndexWriter(dir, config(mergePolicy));
    w.addDocument(doc("a"));
    w.commit();

    Document badChild = doc("child1"); // one byte over the term length limit
    badChild.add(
        new StringField("body", "x".repeat(IndexWriter.MAX_TERM_LENGTH + 1), Field.Store.NO));
    expectThrows(
        IllegalArgumentException.class,
        () -> w.addDocuments(List.of(doc("child0"), badChild, doc("parent"))));
    assertTrue("the rejection is not aborting", w.isOpen());
    w.commit();

    w.addDocument(doc("b"));
    w.commit();
    assertNull(w.getTragicException());
    return w;
  }

  /** For failure messages: maxDoc, live documents and parent field of each committed segment. */
  private static String segments(Directory dir) throws IOException {
    StringBuilder sb = new StringBuilder();
    try (DirectoryReader reader = DirectoryReader.open(dir)) {
      for (LeafReaderContext ctx : reader.leaves()) {
        LeafReader leaf = ctx.reader();
        sb.append(
            String.format(
                Locale.ROOT,
                "[maxDoc=%d numDocs=%d parentField=%s]",
                leaf.maxDoc(),
                leaf.numDocs(),
                leaf.getFieldInfos().getParentField()));
      }
    }
    return sb.toString();
  }

  private static void assertForceMergeWorks(IndexWriter w, String what) throws IOException {
    String before = segments(w.getDirectory());
    try {
      w.forceMerge(1);
    } catch (Throwable t) {
      throw new AssertionError(
          what
              + ": forceMerge(1) of "
              + before
              + " failed; writer open="
              + w.isOpen()
              + ", tragic exception="
              + w.getTragicException(),
          t);
    }
    try (DirectoryReader reader = DirectoryReader.open(w)) {
      assertEquals(2, reader.numDocs());
    }
    w.close();
  }

  public void testSoftDeletesRetentionMergePolicy() throws IOException {
    try (Directory dir = newDirectory()) {
      assertForceMergeWorks(indexWithRejectedBlock(dir, retainAll()), "retention policy");
    }
  }

  public void testKeepFullyDeletedSegment() throws IOException {
    try (Directory dir = newDirectory()) {
      assertForceMergeWorks(
          indexWithRejectedBlock(dir, keepFullyDeleted()), "keepFullyDeletedSegment=true");
    }
  }

  /** The segment is in the commit, so a new writer fails too, even with the default policy. */
  public void testNewWriterOnCommittedIndex() throws IOException {
    try (Directory dir = newDirectory()) {
      indexWithRejectedBlock(dir, retainAll()).close();
      IndexWriter w = new IndexWriter(dir, config(new TieredMergePolicy()));
      assertForceMergeWorks(w, "new writer, TieredMergePolicy");
    }
  }

  /**
   * A reader that holds the segment next to segments with the parent field fails the same check,
   * for example in UnifiedHighlighter.
   */
  public void testMergedFieldInfosOfReader() throws IOException {
    try (Directory dir = newDirectory()) {
      indexWithRejectedBlock(dir, retainAll()).close();
      String segments = segments(dir);
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(segments, "_parent", FieldInfos.getMergedFieldInfos(reader).getParentField());
      }
    }
  }
}
