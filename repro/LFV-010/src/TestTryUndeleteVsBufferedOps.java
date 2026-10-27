import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SoftDeletesDirectoryReaderWrapper;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.Bits;

/**
 * Independent verification of candidate "K": an un-delete done with
 * IndexWriter#tryUpdateDocValue(reader, docID, softDeletesField=null) (the use documented in its
 * javadoc) is not visible to buffered (term-based) operations that are resolved later against the
 * same segment.
 *
 * <p>Written from scratch; only plain public API, ByteBuffersDirectory, NoMergePolicy, default
 * codec. Field names differ from the candidate repro on purpose.
 */
public class TestTryUndeleteVsBufferedOps extends LuceneTestCase {

  static final String SOFT = "__soft";

  private static IndexWriterConfig config() {
    return new IndexWriterConfig()
        .setSoftDeletesField(SOFT)
        .setMergePolicy(NoMergePolicy.INSTANCE)
        .setCommitOnClose(false);
  }

  private static Document doc(String key, boolean bornSoftDeleted) {
    Document d = new Document();
    d.add(new StringField("key", key, Field.Store.YES));
    d.add(new NumericDocValuesField("n", 0));
    if (bornSoftDeleted) {
      d.add(new NumericDocValuesField(SOFT, 1));
    }
    return d;
  }

  /** Number of live documents (per the reader's live docs) whose stored key equals {@code key}. */
  private static int liveCount(DirectoryReader r, String key) throws IOException {
    int c = 0;
    for (LeafReaderContext ctx : r.leaves()) {
      LeafReader lr = ctx.reader();
      Bits live = lr.getLiveDocs();
      for (int d = 0; d < lr.maxDoc(); d++) {
        if ((live == null || live.get(d)) && key.equals(lr.storedFields().document(d).get("key"))) {
          c++;
        }
      }
    }
    return c;
  }

  /** Top-level docID of the (only) doc with this key, ignoring liveness. */
  private static int anyDocId(DirectoryReader r, String key) throws IOException {
    for (LeafReaderContext ctx : r.leaves()) {
      LeafReader lr = ctx.reader();
      for (int d = 0; d < lr.maxDoc(); d++) {
        if (key.equals(lr.storedFields().document(d).get("key"))) {
          return ctx.docBase + d;
        }
      }
    }
    throw new AssertionError("no doc " + key);
  }

  /** value of numeric DV field n for the live doc with this key, or null if no value. */
  private static Long liveN(DirectoryReader r, String key) throws IOException {
    for (LeafReaderContext ctx : r.leaves()) {
      LeafReader lr = ctx.reader();
      Bits live = lr.getLiveDocs();
      for (int d = 0; d < lr.maxDoc(); d++) {
        if ((live == null || live.get(d)) && key.equals(lr.storedFields().document(d).get("key"))) {
          NumericDocValues ndv = lr.getNumericDocValues("n");
          if (ndv != null && ndv.advanceExact(d)) {
            return ndv.longValue();
          }
          return null;
        }
      }
    }
    throw new AssertionError("no live doc " + key);
  }

  private static DirectoryReader openCommitted(Directory dir) throws IOException {
    return new SoftDeletesDirectoryReaderWrapper(DirectoryReader.open(dir), SOFT);
  }

  // ---------------------------------------------------------------- K1 family

  /**
   * K1: soft delete X (term DV update), resolve it with an NRT reader; revive X with
   * tryUpdateDocValue(SOFT=null); then soft delete X again by term. The later soft delete must win.
   */
  public void testK1_termSoftDeleteAfterTryRevive() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, config())) {
      w.addDocument(doc("X", false));
      w.addDocument(doc("Y", false));
      w.commit();
      w.updateDocValues(new Term("key", "X"), new NumericDocValuesField(SOFT, 1));
      try (DirectoryReader r0 = DirectoryReader.open(w)) {
        assertEquals("precondition: X soft-deleted", 0, liveCount(r0, "X"));
        long sRevive =
            w.tryUpdateDocValue(r0, anyDocId(r0, "X"), new NumericDocValuesField(SOFT, null));
        assertTrue("tryUpdateDocValue must succeed", sRevive > 0);
        long sDelete = w.updateDocValues(new Term("key", "X"), new NumericDocValuesField(SOFT, 2));
        assertTrue("seqNo order revive < delete", sRevive < sDelete);
      }
      try (DirectoryReader r1 = DirectoryReader.open(w)) {
        assertEquals("K1: later term soft delete of X must apply", 0, liveCount(r1, "X"));
      }
    }
  }

  /** Control for K1: same, but an NRT refresh between revive and re-delete. */
  public void testK1control_refreshBetweenReviveAndDelete() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, config())) {
      w.addDocument(doc("X", false));
      w.addDocument(doc("Y", false));
      w.commit();
      w.updateDocValues(new Term("key", "X"), new NumericDocValuesField(SOFT, 1));
      try (DirectoryReader r0 = DirectoryReader.open(w)) {
        assertTrue(
            w.tryUpdateDocValue(r0, anyDocId(r0, "X"), new NumericDocValuesField(SOFT, null)) > 0);
      }
      try (DirectoryReader rMid = DirectoryReader.open(w)) {
        assertEquals("revive visible", 1, liveCount(rMid, "X"));
      }
      w.updateDocValues(new Term("key", "X"), new NumericDocValuesField(SOFT, 2));
      try (DirectoryReader r1 = DirectoryReader.open(w)) {
        assertEquals("control: later soft delete applies after a refresh", 0, liveCount(r1, "X"));
      }
    }
  }

  /** K1 variant: a later term-based update of an ordinary numeric DV field after the revive. */
  public void testK1b_termNumericUpdateAfterTryRevive() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, config())) {
      w.addDocument(doc("X", false));
      w.addDocument(doc("Y", false));
      w.commit();
      w.updateDocValues(new Term("key", "X"), new NumericDocValuesField(SOFT, 1));
      try (DirectoryReader r0 = DirectoryReader.open(w)) {
        assertTrue(
            w.tryUpdateDocValue(r0, anyDocId(r0, "X"), new NumericDocValuesField(SOFT, null)) > 0);
        w.updateNumericDocValue(new Term("key", "X"), "n", 42);
      }
      try (DirectoryReader r1 = DirectoryReader.open(w)) {
        assertEquals("X revived", 1, liveCount(r1, "X"));
        assertEquals("K1b: later term update n=42 on revived X", Long.valueOf(42), liveN(r1, "X"));
      }
    }
  }

  /**
   * K1 variant: softUpdateDocument(X, newX) after the revive. The old (revived) X must be
   * soft-deleted, leaving exactly one live X.
   */
  public void testK1c_softUpdateDocumentAfterTryRevive() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, config())) {
      w.addDocument(doc("X", false));
      w.addDocument(doc("Y", false));
      w.commit();
      w.updateDocValues(new Term("key", "X"), new NumericDocValuesField(SOFT, 1));
      try (DirectoryReader r0 = DirectoryReader.open(w)) {
        assertTrue(
            w.tryUpdateDocValue(r0, anyDocId(r0, "X"), new NumericDocValuesField(SOFT, null)) > 0);
        w.softUpdateDocument(new Term("key", "X"), doc("X", false), new NumericDocValuesField(SOFT, 3));
      }
      try (DirectoryReader r1 = DirectoryReader.open(w)) {
        assertEquals("K1c: exactly one live X after softUpdateDocument", 1, liveCount(r1, "X"));
      }
      w.commit();
      try (DirectoryReader rc = openCommitted(dir)) {
        assertEquals("K1c (committed): exactly one live X", 1, liveCount(rc, "X"));
        assertEquals(
            "K1c (committed): term query count",
            1,
            new IndexSearcher(rc).count(new TermQuery(new Term("key", "X"))));
      }
    }
  }

  // ---------------------------------------------------------------- K2 family

  /**
   * K2: segment {Y, X(born soft-deleted)} flushed by an NRT open; buffered hard delete of Y; then
   * revive X with tryUpdateDocValue. The revive must survive the resolution of the delete.
   */
  public void testK2_bufferedHardDeleteOfOtherDocUndoesRevive() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, config())) {
      w.addDocument(doc("Y", false));
      w.addDocument(doc("X", true));
      try (DirectoryReader r0 = DirectoryReader.open(w)) {
        assertEquals("precondition", 0, liveCount(r0, "X"));
        long sDel = w.deleteDocuments(new Term("key", "Y"));
        long sRevive =
            w.tryUpdateDocValue(r0, anyDocId(r0, "X"), new NumericDocValuesField(SOFT, null));
        assertTrue(sRevive > 0 && sDel < sRevive);
      }
      try (DirectoryReader r1 = DirectoryReader.open(w)) {
        assertEquals("Y deleted", 0, liveCount(r1, "Y"));
        assertEquals("K2: X revived", 1, liveCount(r1, "X"));
      }
    }
  }

  /** K2 with a committed segment and an explicit term soft-delete instead of born-deleted. */
  public void testK2b_committedSegment() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, config())) {
      w.addDocument(doc("Y", false));
      w.addDocument(doc("X", false));
      w.commit();
      w.updateDocValues(new Term("key", "X"), new NumericDocValuesField(SOFT, 1));
      try (DirectoryReader r0 = DirectoryReader.open(w)) {
        assertEquals("precondition", 0, liveCount(r0, "X"));
        w.deleteDocuments(new Term("key", "Y"));
        assertTrue(
            w.tryUpdateDocValue(r0, anyDocId(r0, "X"), new NumericDocValuesField(SOFT, null)) > 0);
      }
      try (DirectoryReader r1 = DirectoryReader.open(w)) {
        assertEquals("Y deleted", 0, liveCount(r1, "Y"));
        assertEquals("K2b: X revived", 1, liveCount(r1, "X"));
      }
      w.commit();
      try (DirectoryReader rc = openCommitted(dir)) {
        assertEquals("K2b (committed): X revived", 1, liveCount(rc, "X"));
      }
    }
  }

  /** K2 control: revive alone (no buffered op). */
  public void testK2control_reviveAlone() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, config())) {
      w.addDocument(doc("Y", false));
      w.addDocument(doc("X", true));
      try (DirectoryReader r0 = DirectoryReader.open(w)) {
        assertTrue(
            w.tryUpdateDocValue(r0, anyDocId(r0, "X"), new NumericDocValuesField(SOFT, null)) > 0);
      }
      try (DirectoryReader r1 = DirectoryReader.open(w)) {
        assertEquals("control: X revived", 1, liveCount(r1, "X"));
      }
    }
  }

  /** K2 control: the buffered delete targets a doc in a DIFFERENT segment. */
  public void testK2control_deleteInOtherSegment() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, config())) {
      w.addDocument(doc("Z", false));
      w.commit(); // segment _0 = {Z}
      w.addDocument(doc("Y", false));
      w.addDocument(doc("X", true));
      try (DirectoryReader r0 = DirectoryReader.open(w)) { // segment _1 = {Y, X}
        w.deleteDocuments(new Term("key", "Z"));
        assertTrue(
            w.tryUpdateDocValue(r0, anyDocId(r0, "X"), new NumericDocValuesField(SOFT, null)) > 0);
      }
      try (DirectoryReader r1 = DirectoryReader.open(w)) {
        assertEquals("Z deleted", 0, liveCount(r1, "Z"));
        assertEquals("X revived (delete in other segment)", 1, liveCount(r1, "X"));
      }
    }
  }

  /** K2 ordering variant: hard delete of Y issued AFTER the revive. */
  public void testK2c_bufferedHardDeleteAfterRevive() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, config())) {
      w.addDocument(doc("Y", false));
      w.addDocument(doc("X", true));
      try (DirectoryReader r0 = DirectoryReader.open(w)) {
        assertTrue(
            w.tryUpdateDocValue(r0, anyDocId(r0, "X"), new NumericDocValuesField(SOFT, null)) > 0);
        w.deleteDocuments(new Term("key", "Y"));
      }
      try (DirectoryReader r1 = DirectoryReader.open(w)) {
        assertEquals("Y deleted", 0, liveCount(r1, "Y"));
        assertEquals("K2c: X revived", 1, liveCount(r1, "X"));
      }
    }
  }

  /** CheckIndex after each K scenario's commit, to see whether on-disk counts stay consistent. */
  public void testK2_checkIndexAfterCommit() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, config())) {
        w.addDocument(doc("Y", false));
        w.addDocument(doc("X", true));
        try (DirectoryReader r0 = DirectoryReader.open(w)) {
          w.deleteDocuments(new Term("key", "Y"));
          assertTrue(
              w.tryUpdateDocValue(r0, anyDocId(r0, "X"), new NumericDocValuesField(SOFT, null))
                  > 0);
        }
        w.commit();
      }
      TestUtil.checkIndex(dir);
      try (DirectoryReader rc = openCommitted(dir)) {
        assertEquals("K2 (committed, after close): X revived", 1, liveCount(rc, "X"));
      }
    }
  }
  // ---------------------------------------------------------------- K2 mechanism probes

  /**
   * K2 probe: the segment has a third, live doc Z, so deleting Y does NOT make the segment look
   * 100% deleted. If K2 is caused by the fully-deleted-segment drop, this passes.
   */
  public void testK2probe_segmentNotFullyDeleted() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, config())) {
      w.addDocument(doc("Y", false));
      w.addDocument(doc("X", true));
      w.addDocument(doc("Z", false));
      try (DirectoryReader r0 = DirectoryReader.open(w)) {
        w.deleteDocuments(new Term("key", "Y"));
        assertTrue(
            w.tryUpdateDocValue(r0, anyDocId(r0, "X"), new NumericDocValuesField(SOFT, null)) > 0);
      }
      try (DirectoryReader r1 = DirectoryReader.open(w)) {
        assertEquals("Y deleted", 0, liveCount(r1, "Y"));
        assertEquals("probe: X revived when segment not fully deleted", 1, liveCount(r1, "X"));
        assertEquals("Z live", 1, liveCount(r1, "Z"));
      }
    }
  }

  /** K2 probe: the other doc is removed with tryDeleteDocument (no buffered op at all). */
  public void testK2d_tryDeleteOfOtherDocDropsRevivedDoc() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, config())) {
      w.addDocument(doc("Y", false));
      w.addDocument(doc("X", true));
      try (DirectoryReader r0 = DirectoryReader.open(w)) {
        assertTrue(
            w.tryUpdateDocValue(r0, anyDocId(r0, "X"), new NumericDocValuesField(SOFT, null)) > 0);
        assertTrue(w.tryDeleteDocument(r0, anyDocId(r0, "Y")) > 0);
      }
      try (DirectoryReader r1 = DirectoryReader.open(w)) {
        assertEquals("Y deleted", 0, liveCount(r1, "Y"));
        assertEquals("K2d: X revived (try* only)", 1, liveCount(r1, "X"));
      }
    }
  }

  /**
   * K2 probe: SoftDeletesRetentionMergePolicy(MatchAll) keeps fully deleted segments, so the drop
   * should not happen.
   */
  public void testK2probe_retentionMergePolicyKeepsSegment() throws Exception {
    IndexWriterConfig c =
        config()
            .setMergePolicy(
                new org.apache.lucene.index.SoftDeletesRetentionMergePolicy(
                    SOFT,
                    org.apache.lucene.search.MatchAllDocsQuery::new,
                    NoMergePolicy.INSTANCE));
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, c)) {
      w.addDocument(doc("Y", false));
      w.addDocument(doc("X", true));
      try (DirectoryReader r0 = DirectoryReader.open(w)) {
        w.deleteDocuments(new Term("key", "Y"));
        assertTrue(
            w.tryUpdateDocValue(r0, anyDocId(r0, "X"), new NumericDocValuesField(SOFT, null)) > 0);
      }
      try (DirectoryReader r1 = DirectoryReader.open(w)) {
        assertEquals("Y deleted", 0, liveCount(r1, "Y"));
        assertEquals("probe: X revived under retention MP", 1, liveCount(r1, "X"));
      }
    }
  }
  // ---------------------------------------------------------------- durability / MP independence

  /** K1c, checked only on the committed index (after close), plus CheckIndex. */
  public void testK1c_duplicatePersistsInCommit() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, config())) {
        w.addDocument(doc("X", false));
        w.addDocument(doc("Y", false));
        w.commit();
        w.updateDocValues(new Term("key", "X"), new NumericDocValuesField(SOFT, 1));
        try (DirectoryReader r0 = DirectoryReader.open(w)) {
          assertTrue(
              w.tryUpdateDocValue(r0, anyDocId(r0, "X"), new NumericDocValuesField(SOFT, null))
                  > 0);
          w.softUpdateDocument(
              new Term("key", "X"), doc("X", false), new NumericDocValuesField(SOFT, 3));
        }
        w.commit();
      }
      TestUtil.checkIndex(dir);
      try (DirectoryReader rc = openCommitted(dir)) {
        assertEquals(
            "K1c (committed): term query count for key:X",
            1,
            new IndexSearcher(rc).count(new TermQuery(new Term("key", "X"))));
      }
    }
  }

  /** K1 under SoftDeletesRetentionMergePolicy(MatchAll): independent of the merge policy. */
  public void testK1_retentionMergePolicy() throws Exception {
    IndexWriterConfig c =
        config()
            .setMergePolicy(
                new org.apache.lucene.index.SoftDeletesRetentionMergePolicy(
                    SOFT,
                    org.apache.lucene.search.MatchAllDocsQuery::new,
                    NoMergePolicy.INSTANCE));
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, c)) {
      w.addDocument(doc("X", false));
      w.addDocument(doc("Y", false));
      w.commit();
      w.updateDocValues(new Term("key", "X"), new NumericDocValuesField(SOFT, 1));
      try (DirectoryReader r0 = DirectoryReader.open(w)) {
        assertTrue(
            w.tryUpdateDocValue(r0, anyDocId(r0, "X"), new NumericDocValuesField(SOFT, null)) > 0);
        w.updateDocValues(new Term("key", "X"), new NumericDocValuesField(SOFT, 2));
      }
      try (DirectoryReader r1 = DirectoryReader.open(w)) {
        assertEquals("K1 (retention MP): later soft delete must apply", 0, liveCount(r1, "X"));
      }
    }
  }
}
