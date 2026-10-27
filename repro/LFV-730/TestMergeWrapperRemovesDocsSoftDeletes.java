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
package verify.v1;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.CodecReader;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FilterCodecReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.LogDocMergePolicy;
import org.apache.lucene.index.MergePolicy;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.OneMergeWrappingMergePolicy;
import org.apache.lucene.index.SegmentCommitInfo;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.index.SoftDeletesRetentionMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.FixedBitSet;

/**
 * Independent verification (public API only, package outside org.apache.lucene).
 *
 * <p>Claim: when a soft-deletes field is configured, IndexWriter derives the soft-delete count of
 * a merged segment for input segments without hard deletes as {@code softDelCount -
 * wrapped.numDeletedDocs()}. A OneMerge#wrapForMerge that removes documents (documented: "Wrap a
 * reader prior to merging in order to add/remove fields or documents") makes that count too small.
 *
 * <p>The merge wrapper below hides every document whose "drop" doc value is 1. It always returns a
 * wrapped reader whose live docs are the input live docs minus the hidden documents.
 */
public class TestMergeWrapperRemovesDocsSoftDeletes extends LuceneTestCase {

  private static final String SOFT = "soft_delete";

  private static CodecReader hideMarked(CodecReader reader) throws IOException {
    final int maxDoc = reader.maxDoc();
    final FixedBitSet live = new FixedBitSet(maxDoc);
    final Bits in = reader.getLiveDocs();
    for (int i = 0; i < maxDoc; i++) {
      if (in == null || in.get(i)) {
        live.set(i);
      }
    }
    NumericDocValues drop = reader.getNumericDocValues("drop");
    if (drop != null) {
      for (int d = drop.nextDoc(); d != DocIdSetIterator.NO_MORE_DOCS; d = drop.nextDoc()) {
        if (drop.longValue() == 1) {
          live.clear(d);
        }
      }
    }
    final int numDocs = live.cardinality();
    return new FilterCodecReader(reader) {
      @Override
      public Bits getLiveDocs() {
        return live;
      }

      @Override
      public int numDocs() {
        return numDocs;
      }

      @Override
      public CacheHelper getCoreCacheHelper() {
        return in.getCoreCacheHelper();
      }

      @Override
      public CacheHelper getReaderCacheHelper() {
        return null; // live docs differ from the wrapped reader
      }
    };
  }

  /** A merge policy whose merges hide documents with drop=1. */
  private static MergePolicy dropping(MergePolicy in) {
    return new OneMergeWrappingMergePolicy(
        in,
        toWrap ->
            new MergePolicy.OneMerge(toWrap.segments) {
              @Override
              public CodecReader wrapForMerge(CodecReader reader) throws IOException {
                return hideMarked(toWrap.wrapForMerge(reader));
              }
            });
  }

  private static IndexWriterConfig config(String softField, MergePolicy mp) {
    IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
    if (softField != null) {
      iwc.setSoftDeletesField(softField);
    }
    iwc.setCodec(TestUtil.getDefaultCodec());
    iwc.setMergePolicy(mp);
    iwc.setMergeScheduler(new SerialMergeScheduler());
    iwc.setMaxBufferedDocs(1000);
    return iwc;
  }

  private static Document doc(String id, boolean drop, boolean keep) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    d.add(new NumericDocValuesField("drop", drop ? 1 : 0));
    if (keep) {
      d.add(new StringField("keep", "yes", Field.Store.NO));
    }
    return d;
  }

  private static void forceMergeExpectSuccess(IndexWriter w) throws IOException {
    try {
      w.forceMerge(1);
    } catch (Throwable t) {
      throw new AssertionError(
          "forceMerge(1) with a OneMerge#wrapForMerge that removes a document must succeed, but"
              + " threw: "
              + t,
          t);
    }
  }

  private static int liveDocsByIteration(DirectoryReader r) {
    int live = 0;
    for (LeafReaderContext ctx : r.leaves()) {
      Bits bits = ctx.reader().getLiveDocs();
      for (int i = 0; i < ctx.reader().maxDoc(); i++) {
        if (bits == null || bits.get(i)) {
          live++;
        }
      }
    }
    return live;
  }

  /**
   * Runs the test framework's CheckIndex (all checks, including the soft-delete count check; note
   * that the CheckIndex API/CLI default level on 10.x/main is checksum-only, which skips it).
   * Returns null if clean, else the error lines.
   */
  private static String checkIndex(Directory dir) throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    PrintStream saved = System.out;
    try {
      System.setOut(new PrintStream(bos, true, StandardCharsets.UTF_8));
      TestUtil.checkIndex(dir);
      return null;
    } catch (RuntimeException e) {
      String out = bos.toString(StandardCharsets.UTF_8);
      StringBuilder sb = new StringBuilder(e.getMessage()).append(": ");
      for (String line : out.split("\n")) {
        if (line.contains("ERROR") || line.contains("soft deletes")) {
          sb.append(line.trim()).append(" | ");
        }
      }
      return sb.toString();
    } finally {
      System.setOut(saved);
    }
  }

  /** Builds seg1 {a,b} and seg2 {c, d(drop)}; merges to one segment; checks it holds a,b,c. */
  private void runSimpleDrop(String softField) throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, config(softField, dropping(new LogDocMergePolicy())))) {
        w.addDocument(doc("a", false, false));
        w.addDocument(doc("b", false, false));
        w.commit();
        w.addDocument(doc("c", false, false));
        w.addDocument(doc("d", true, false));
        w.commit();
        assertEquals(2, SegmentInfos.readLatestCommit(dir).size());

        forceMergeExpectSuccess(w);
        w.commit();

        SegmentInfos sis = SegmentInfos.readLatestCommit(dir);
        assertEquals(1, sis.size());
        SegmentCommitInfo sci = sis.info(0);
        assertEquals("merged maxDoc (a,b,c)", 3, sci.info.maxDoc());
        assertEquals("merged delCount", 0, sci.getDelCount());
        assertEquals("merged softDelCount", 0, sci.getSoftDelCount());
        try (DirectoryReader r = DirectoryReader.open(w)) {
          assertEquals(3, r.numDocs());
          assertEquals(3, new IndexSearcher(r).count(new MatchAllDocsQuery()));
          assertEquals(0, new IndexSearcher(r).count(new TermQuery(new Term("id", "d"))));
        }
      }
      assertNull("CheckIndex must be clean", checkIndex(dir));
    }
  }

  /** Control: the same merge without a soft-deletes field. Expected to pass on every version. */
  public void testControlNoSoftDeletesField() throws IOException {
    runSimpleDrop(null);
  }

  /**
   * Bug: identical merge, only a soft-deletes field is configured and nothing is soft-deleted.
   * Expected to behave exactly as the control.
   */
  public void testDropWithSoftDeletesFieldConfigured() throws IOException {
    runSimpleDrop(SOFT);
  }

  /**
   * Control for the root cause: same as the bug test, but the segment with the removed document
   * also has one hard delete, so IndexWriter takes the counting path instead of the subtraction
   * shortcut. Expected to pass on every affected version.
   */
  public void testControlDropWithHardDeleteInSameSegment() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, config(SOFT, dropping(new LogDocMergePolicy())))) {
        w.addDocument(doc("a", false, false));
        w.addDocument(doc("b", false, false));
        w.commit();
        w.addDocument(doc("c", false, false));
        w.addDocument(doc("d", true, false));
        w.addDocument(doc("x", false, false));
        w.commit();
        w.deleteDocuments(new Term("id", "x")); // hard delete in the segment with the removed doc
        w.commit();

        forceMergeExpectSuccess(w);
        w.commit();

        SegmentInfos sis = SegmentInfos.readLatestCommit(dir);
        assertEquals(1, sis.size());
        SegmentCommitInfo sci = sis.info(0);
        assertEquals("merged maxDoc (a,b,c)", 3, sci.info.maxDoc());
        assertEquals(0, sci.getDelCount());
        assertEquals(0, sci.getSoftDelCount());
        try (DirectoryReader r = DirectoryReader.open(w)) {
          assertEquals(3, r.numDocs());
          assertEquals(3, new IndexSearcher(r).count(new MatchAllDocsQuery()));
        }
      }
      assertNull("CheckIndex must be clean", checkIndex(dir));
    }
  }

  /**
   * Bug, silent variant: SoftDeletesRetentionMergePolicy (retains docs with keep=yes) over the
   * dropping policy. seg1 = {a(keep), b, c}, a and b soft-deleted; seg2 = {d, e(drop)}. The merged
   * segment must hold a (retained, soft-deleted), c and d: maxDoc 3, softDelCount 1, numDocs 2.
   */
  public void testRetentionOverDroppingWrapper() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      MergePolicy mp =
          new SoftDeletesRetentionMergePolicy(
              SOFT, () -> new TermQuery(new Term("keep", "yes")), dropping(new LogDocMergePolicy()));
      try (IndexWriter w = new IndexWriter(dir, config(SOFT, mp))) {
        w.addDocument(doc("a", false, true));
        w.addDocument(doc("b", false, false));
        w.addDocument(doc("c", false, false));
        w.commit();
        w.updateDocValues(new Term("id", "a"), new NumericDocValuesField(SOFT, 1));
        w.updateDocValues(new Term("id", "b"), new NumericDocValuesField(SOFT, 1));
        w.commit();
        w.addDocument(doc("d", false, false));
        w.addDocument(doc("e", true, false));
        w.commit();
        try (DirectoryReader r = DirectoryReader.open(w)) {
          assertEquals("before merge: c, d, e are live", 3, r.numDocs());
        }

        forceMergeExpectSuccess(w);
        w.commit();

        SegmentInfos sis = SegmentInfos.readLatestCommit(dir);
        assertEquals(1, sis.size());
        SegmentCommitInfo sci = sis.info(0);
        assertEquals("merged maxDoc (a retained, c, d)", 3, sci.info.maxDoc());
        assertEquals("merged delCount", 0, sci.getDelCount());
        try (DirectoryReader r = DirectoryReader.open(w)) {
          int live = liveDocsByIteration(r);
          int count = new IndexSearcher(r).count(new MatchAllDocsQuery());
          String state =
              "softDelCount="
                  + sci.getSoftDelCount()
                  + " numDocs="
                  + r.numDocs()
                  + " count(MatchAll)="
                  + count
                  + " liveDocsByIteration="
                  + live
                  + " writer.getDocStats().numDocs="
                  + w.getDocStats().numDocs;
          assertEquals("live docs by iteration [" + state + "]", 2, live);
          assertEquals("merged softDelCount (a is retained soft-deleted) [" + state + "]", 1,
              sci.getSoftDelCount());
          assertEquals("DirectoryReader#numDocs [" + state + "]", 2, r.numDocs());
          assertEquals("IndexSearcher#count(MatchAllDocsQuery) [" + state + "]", 2, count);
          assertEquals("IndexWriter#getDocStats().numDocs [" + state + "]", 2,
              w.getDocStats().numDocs);
        }
      }
      String ci = checkIndex(dir);
      assertNull("CheckIndex must be clean, got: " + ci, ci);
    }
  }

  /** Same scenario as testRetentionOverDroppingWrapper; asserts only that CheckIndex is clean. */
  public void testRetentionOverDroppingWrapperCheckIndex() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      MergePolicy mp =
          new SoftDeletesRetentionMergePolicy(
              SOFT, () -> new TermQuery(new Term("keep", "yes")), dropping(new LogDocMergePolicy()));
      try (IndexWriter w = new IndexWriter(dir, config(SOFT, mp))) {
        w.addDocument(doc("a", false, true));
        w.addDocument(doc("b", false, false));
        w.addDocument(doc("c", false, false));
        w.commit();
        w.updateDocValues(new Term("id", "a"), new NumericDocValuesField(SOFT, 1));
        w.updateDocValues(new Term("id", "b"), new NumericDocValuesField(SOFT, 1));
        w.commit();
        w.addDocument(doc("d", false, false));
        w.addDocument(doc("e", true, false));
        w.commit();
        forceMergeExpectSuccess(w);
        w.commit();
      }
      String ci = checkIndex(dir);
      assertNull("CheckIndex must be clean, got: " + ci, ci);
    }
  }

  /**
   * Bug, escalation: after the merge above, the retention query stops matching; a later merge of
   * the merged segment with a new segment {f} must succeed and purge a.
   */
  public void testLaterMergeAfterRetentionEnds() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      AtomicBoolean retain = new AtomicBoolean(true);
      MergePolicy mp =
          new SoftDeletesRetentionMergePolicy(
              SOFT,
              () -> retain.get() ? (Query) new TermQuery(new Term("keep", "yes")) : new MatchNoDocsQuery(),
              dropping(new LogDocMergePolicy()));
      try (IndexWriter w = new IndexWriter(dir, config(SOFT, mp))) {
        w.addDocument(doc("a", false, true));
        w.addDocument(doc("b", false, false));
        w.addDocument(doc("c", false, false));
        w.commit();
        w.updateDocValues(new Term("id", "a"), new NumericDocValuesField(SOFT, 1));
        w.updateDocValues(new Term("id", "b"), new NumericDocValuesField(SOFT, 1));
        w.commit();
        w.addDocument(doc("d", false, false));
        w.addDocument(doc("e", true, false));
        w.commit();
        forceMergeExpectSuccess(w); // first merge (same as testRetentionOverDroppingWrapper)
        w.commit();

        retain.set(false);
        w.addDocument(doc("f", false, false));
        w.commit();
        forceMergeExpectSuccess(w); // second merge: a is no longer retained
        w.commit();

        SegmentInfos sis = SegmentInfos.readLatestCommit(dir);
        assertEquals(1, sis.size());
        SegmentCommitInfo sci = sis.info(0);
        assertEquals("merged maxDoc (c, d, f)", 3, sci.info.maxDoc());
        assertEquals(0, sci.getSoftDelCount());
        try (DirectoryReader r = DirectoryReader.open(w)) {
          assertEquals(3, r.numDocs());
          assertEquals(3, liveDocsByIteration(r));
        }
      }
      String ci = checkIndex(dir);
      assertNull("CheckIndex must be clean, got: " + ci, ci);
    }
  }
}
