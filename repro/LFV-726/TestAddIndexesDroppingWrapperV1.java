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
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.store.BaseDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.FixedBitSet;

/**
 * Independent verifier repro: IndexWriter#addIndexes(CodecReader...) when the merge policy's
 * OneMerge#wrapForMerge removes documents ("Wrap a reader prior to merging in order to add/remove
 * fields or documents").
 *
 * <p>Same package only for IndexWriter.setMaxDocs (the test hook TestIndexWriterMaxDocs uses), in
 * the admission test. Everything else is public API.
 */
public class TestAddIndexesDroppingWrapperV1 extends LuceneTestCase {

  private static final String SOFT = "soft";
  private static final String DROP = "drop";

  /** Removes every live document that has DROP == 1, and (optionally) every soft-deleted one. */
  static CodecReader dropDocs(CodecReader in, boolean dropSoftDeleted) throws IOException {
    final int maxDoc = in.maxDoc();
    final Bits live = in.getLiveDocs();
    final FixedBitSet keep = new FixedBitSet(maxDoc);
    NumericDocValues drop = in.getNumericDocValues(DROP);
    NumericDocValues soft = in.getNumericDocValues(SOFT);
    for (int d = 0; d < maxDoc; d++) {
      if (live != null && live.get(d) == false) {
        continue;
      }
      boolean remove = drop != null && drop.advanceExact(d) && drop.longValue() == 1;
      if (dropSoftDeleted && soft != null && soft.advanceExact(d)) {
        remove = true;
      }
      if (remove == false) {
        keep.set(d);
      }
    }
    final int numDocs = keep.cardinality();
    return new FilterCodecReader(in) {
      @Override
      public Bits getLiveDocs() {
        return keep;
      }

      @Override
      public int numDocs() {
        return numDocs;
      }

      @Override
      public CacheHelper getCoreCacheHelper() {
        return null;
      }

      @Override
      public CacheHelper getReaderCacheHelper() {
        return null;
      }
    };
  }

  /** A policy whose addIndexes merge removes documents in wrapForMerge. */
  static final class DroppingAddIndexesPolicy extends FilterMergePolicy {
    final boolean identity;
    final boolean dropSoftDeleted;

    DroppingAddIndexesPolicy(boolean identity, boolean dropSoftDeleted) {
      super(NoMergePolicy.INSTANCE);
      this.identity = identity;
      this.dropSoftDeleted = dropSoftDeleted;
    }

    @Override
    public MergeSpecification findMerges(CodecReader... readers) throws IOException {
      MergeSpecification spec = new MergeSpecification();
      spec.add(
          new OneMerge(readers) {
            @Override
            public CodecReader wrapForMerge(CodecReader reader) throws IOException {
              return identity ? reader : dropDocs(reader, dropSoftDeleted);
            }
          });
      return spec;
    }
  }

  private static Document doc(String id, boolean drop, boolean softDeleted) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    d.add(new NumericDocValuesField(DROP, drop ? 1 : 0));
    if (softDeleted) {
      d.add(new NumericDocValuesField(SOFT, 1));
    }
    return d;
  }

  /** One-segment source index: ids "0".."n-1"; the ones in dropIds carry DROP=1. */
  private static Directory sourceIndex(int n, int... dropIds) throws IOException {
    Directory src = newDirectory();
    IndexWriterConfig cfg = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(src, cfg)) {
      for (int i = 0; i < n; i++) {
        boolean drop = false;
        for (int x : dropIds) {
          drop |= x == i;
        }
        w.addDocument(doc(Integer.toString(i), drop, false));
      }
      w.commit();
    }
    return src;
  }

  private static CodecReader[] codecLeaves(DirectoryReader r) {
    List<CodecReader> out = new ArrayList<>();
    for (LeafReaderContext ctx : r.leaves()) {
      out.add((CodecReader) ctx.reader());
    }
    return out.toArray(new CodecReader[0]);
  }

  /** Closes the writer; records IndexWriter's own close-time assertion (with -ea) as a problem. */
  private static void close(IndexWriter w, List<String> problems) throws IOException {
    try {
      w.close();
    } catch (AssertionError e) {
      problems.add("IndexWriter.close assertion: " + e.getMessage());
    }
  }

  /** CheckIndex with slow checks (includes the soft-delete count check); null if clean. */
  private static String checkIndex(Directory dir) {
    try {
      TestUtil.checkIndex(dir);
      return null;
    } catch (Throwable t) {
      return t + " (CheckIndex output printed above in the log)";
    }
  }

  /**
   * After addIndexes of 5 documents of which the wrapper removes 2, the index holds 3 documents;
   * getPendingNumDocs ("the number of documents in the index including documents being added")
   * must be 3 once addIndexes has returned. A second identical call must bring it to 6.
   */
  public void testPendingNumDocsAfterAddIndexes() throws IOException {
    List<String> problems = new ArrayList<>();
    try (Directory src = sourceIndex(5, 1, 3);
        Directory dst = newDirectory();
        DirectoryReader srcReader = DirectoryReader.open(src)) {
      IndexWriterConfig cfg =
          new IndexWriterConfig().setMergePolicy(new DroppingAddIndexesPolicy(false, false));
      IndexWriter w = new IndexWriter(dst, cfg);
      w.addIndexes(codecLeaves(srcReader));
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(dst)) {
        // non-vacuity: the wrapper's removal really took effect
        assertEquals("documents written by addIndexes", 3, r.maxDoc());
      }
      assertEquals("DocStats.maxDoc", 3, w.getDocStats().maxDoc);
      long afterFirst = w.getPendingNumDocs();
      w.addIndexes(codecLeaves(srcReader));
      w.commit();
      assertEquals("DocStats.maxDoc after 2nd call", 6, w.getDocStats().maxDoc);
      long afterSecond = w.getPendingNumDocs();
      if (afterFirst != 3 || afterSecond != 6) {
        problems.add(
            "getPendingNumDocs after 1st/2nd addIndexes = "
                + afterFirst
                + "/"
                + afterSecond
                + " but the index holds 3/6 documents");
      }
      close(w, problems);
      // a freshly opened writer on the same index recomputes the count from the segments
      try (IndexWriter w2 = new IndexWriter(dst, new IndexWriterConfig())) {
        if (w2.getPendingNumDocs() != afterSecond) {
          problems.add(
              "reopened writer reports getPendingNumDocs=" + w2.getPendingNumDocs());
        }
      }
    }
    assertTrue(String.join("; ", problems), problems.isEmpty());
  }

  /**
   * User-visible effect of the over-reservation: with the document limit set to 5 (test hook used
   * by TestIndexWriterMaxDocs), an index holding 3 documents must accept a 4th one.
   */
  public void testDocLimitAdmissionAfterAddIndexes() throws IOException {
    try (Directory src = sourceIndex(5, 1, 3);
        Directory dst = newDirectory();
        DirectoryReader srcReader = DirectoryReader.open(src)) {
      IndexWriterConfig cfg =
          new IndexWriterConfig().setMergePolicy(new DroppingAddIndexesPolicy(false, false));
      List<String> problems = new ArrayList<>();
      IndexWriter w = new IndexWriter(dst, cfg);
      w.addIndexes(codecLeaves(srcReader));
      w.commit();
      assertEquals(3, w.getDocStats().maxDoc);
      IndexWriter.setMaxDocs(5);
      try {
        w.addDocument(doc("new", false, false));
      } catch (IllegalArgumentException e) {
        problems.add("index holds 3 documents, limit is 5, but adding a 4th was rejected: " + e);
      } finally {
        IndexWriter.setMaxDocs(IndexWriter.MAX_DOCS);
      }
      close(w, problems);
      assertTrue(String.join("; ", problems), problems.isEmpty());
    }
  }

  /**
   * Soft deletes: the source holds a (soft-deleted), b, c. The destination writer uses the same
   * soft-deletes field and its addIndexes wrapper purges soft-deleted documents. The new segment
   * holds b and c, none soft-deleted: its recorded soft-delete count must be 0, DocStats.numDocs
   * 2, and CheckIndex must be clean.
   */
  public void testSoftDeleteCountAfterAddIndexes() throws IOException {
    try (Directory src = newDirectory();
        BaseDirectoryWrapper dst = newDirectory()) {
      dst.setCheckIndexOnClose(false); // checked explicitly below
      try (IndexWriter w =
          new IndexWriter(
              src,
              new IndexWriterConfig()
                  .setSoftDeletesField(SOFT)
                  .setMergePolicy(NoMergePolicy.INSTANCE))) {
        w.addDocument(doc("a", false, true));
        w.addDocument(doc("b", false, false));
        w.addDocument(doc("c", false, false));
        w.commit();
      }
      List<String> problems = new ArrayList<>();
      try (DirectoryReader srcReader = DirectoryReader.open(src)) {
        IndexWriterConfig cfg =
            new IndexWriterConfig()
                .setSoftDeletesField(SOFT)
                .setMergePolicy(new DroppingAddIndexesPolicy(false, true));
        IndexWriter w = new IndexWriter(dst, cfg);
        w.addIndexes(codecLeaves(srcReader));
        w.commit();
        int numDocs = w.getDocStats().numDocs;
        if (numDocs != 2) {
          problems.add("DocStats.numDocs=" + numDocs + " (expected 2)");
        }
        close(w, problems);
      }
      SegmentInfos sis = SegmentInfos.readLatestCommit(dst);
      assertEquals(1, sis.size());
      SegmentCommitInfo sci = sis.info(0);
      int actualSoft;
      try (DirectoryReader r = DirectoryReader.open(dst)) {
        assertEquals("documents written (non-vacuity)", 2, r.maxDoc());
        NumericDocValues sv = MultiDocValues.getNumericValues(r, SOFT);
        actualSoft = 0;
        if (sv != null) {
          while (sv.nextDoc() != NumericDocValues.NO_MORE_DOCS) {
            actualSoft++;
          }
        }
      }
      if (sci.getSoftDelCount() != actualSoft) {
        problems.add(
            "committed softDelCount=" + sci.getSoftDelCount() + " but segment has " + actualSoft);
      }
      String ci = checkIndex(dst);
      if (ci != null) {
        problems.add("CheckIndex: " + ci);
      }
      assertTrue(String.join("; ", problems), problems.isEmpty());
    }
  }

  /**
   * Consequence probe: source a (soft-deleted), b (DROP=1), c. The wrapper removes a and b, so the
   * new segment holds only c, which is live. The recorded soft-delete count (1) then equals the
   * segment's maxDoc. Expected: c stays in the index across commit, NRT reopen, commit.
   */
  public void testLiveDocumentSurvivesWhenOverCountReachesMaxDoc() throws IOException {
    try (Directory src = newDirectory();
        BaseDirectoryWrapper dst = newDirectory()) {
      dst.setCheckIndexOnClose(false);
      try (IndexWriter w =
          new IndexWriter(
              src,
              new IndexWriterConfig()
                  .setSoftDeletesField(SOFT)
                  .setMergePolicy(NoMergePolicy.INSTANCE))) {
        w.addDocument(doc("a", false, true));
        w.addDocument(doc("b", true, false));
        w.addDocument(doc("c", false, false));
        w.commit();
      }
      List<String> problems = new ArrayList<>();
      try (DirectoryReader srcReader = DirectoryReader.open(src)) {
        IndexWriterConfig cfg =
            new IndexWriterConfig()
                .setSoftDeletesField(SOFT)
                .setMergePolicy(new DroppingAddIndexesPolicy(false, true));
        IndexWriter w = new IndexWriter(dst, cfg);
        w.addIndexes(codecLeaves(srcReader));
        w.commit();
        try (DirectoryReader nrt = DirectoryReader.open(w)) {
          IndexSearcher s = newSearcher(nrt);
          int hitsC = s.search(new TermQuery(new Term("id", "c")), 10).scoreDocs.length;
          if (hitsC != 1) {
            problems.add("NRT search id:c hits=" + hitsC);
          }
        } catch (AssertionError e) {
          problems.add("assertion while opening/searching NRT reader: " + e.getMessage());
        }
        try {
          w.commit();
          if (w.getDocStats().numDocs != 1) {
            problems.add("DocStats.numDocs=" + w.getDocStats().numDocs);
          }
          close(w, problems);
        } catch (AlreadyClosedException e) {
          problems.add("writer closed by the earlier failure: " + e.getMessage());
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dst)) {
        int hitsC = newSearcher(r).search(new TermQuery(new Term("id", "c")), 10).scoreDocs.length;
        if (hitsC != 1) {
          problems.add(
              "live document c missing from the committed index: hits="
                  + hitsC
                  + " maxDoc="
                  + r.maxDoc()
                  + " segments="
                  + r.leaves().size());
        }
      }
      String ci = checkIndex(dst);
      if (ci != null) {
        problems.add("CheckIndex: " + ci);
      }
      assertTrue(String.join("; ", problems), problems.isEmpty());
    }
  }

  /** Control: identity wrapper, same inputs: the accounting is exact. */
  public void testControlIdentityWrapper() throws IOException {
    try (Directory src = sourceIndex(5, 1, 3);
        Directory dst = newDirectory();
        DirectoryReader srcReader = DirectoryReader.open(src)) {
      IndexWriterConfig cfg =
          new IndexWriterConfig().setMergePolicy(new DroppingAddIndexesPolicy(true, false));
      try (IndexWriter w = new IndexWriter(dst, cfg)) {
        w.addIndexes(codecLeaves(srcReader));
        w.commit();
        assertEquals(5, w.getDocStats().maxDoc);
        assertEquals(5, w.getPendingNumDocs());
      }
    }
  }

  /**
   * Control: a regular merge whose OneMerge#wrapForMerge removes the same documents. IndexWriter
   * deducts totalMaxDoc - merged maxDoc there, so getPendingNumDocs follows the removal.
   */
  public void testControlRegularMergeHonorsRemoval() throws IOException {
    try (Directory dst = newDirectory()) {
      MergePolicy mp =
          new OneMergeWrappingMergePolicy(
              new LogDocMergePolicy(),
              m ->
                  new MergePolicy.OneMerge(m.segments) {
                    @Override
                    public CodecReader wrapForMerge(CodecReader reader) throws IOException {
                      return dropDocs(reader, false);
                    }
                  });
      try (IndexWriter w = new IndexWriter(dst, new IndexWriterConfig().setMergePolicy(mp))) {
        for (int i = 0; i < 5; i++) {
          w.addDocument(doc(Integer.toString(i), i == 1 || i == 3, false));
          if (i == 2) {
            w.flush();
          }
        }
        w.forceMerge(1);
        w.commit();
        assertEquals(3, w.getDocStats().maxDoc);
        assertEquals(3, w.getPendingNumDocs());
      }
    }
  }
}
