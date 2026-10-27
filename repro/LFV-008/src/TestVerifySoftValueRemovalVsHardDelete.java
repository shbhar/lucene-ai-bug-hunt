import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.CheckIndex;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.SegmentCommitInfo;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.Bits;

/**
 * Independent verification of candidate bug "I": removing the soft-deletes doc value (field value
 * null) of a document that is (or becomes, before the update is written) HARD-deleted.
 *
 * <p>All tests are deterministic: plain ByteBuffersDirectory, explicit IndexWriterConfig,
 * NoMergePolicy, no randomized components. Doc ids are a, b, c (doc 0, 1, 2 of one segment).
 *
 * <p>Expected per IndexWriterConfig#setSoftDeletesField javadoc: "Hard-deleted documents won't be
 * considered as soft-deleted even if they have a value in the soft-deletes field." Removing a
 * soft-deletes value can at most turn a soft-deleted doc back into a live one; it can never revive
 * a hard-deleted doc, and the per-segment counts must stay consistent.
 */
@LuceneTestCase.SuppressSysoutChecks(bugUrl = "prints diagnostics")
public class TestVerifySoftValueRemovalVsHardDelete extends LuceneTestCase {

  private static final String SD = "soft_delete";

  private static Document doc(String id) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    return d;
  }

  private static IndexWriterConfig config() {
    return new IndexWriterConfig()
        .setSoftDeletesField(SD)
        .setMergePolicy(NoMergePolicy.INSTANCE)
        .setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH)
        .setRAMBufferSizeMB(64);
  }

  private static NumericDocValuesField removeSoft() {
    return new NumericDocValuesField(SD, (Long) null);
  }

  /** Live ids by walking leaf live docs and stored fields (no counts involved). */
  private static List<String> liveIds(DirectoryReader r) throws Exception {
    List<String> ids = new ArrayList<>();
    for (LeafReaderContext ctx : r.leaves()) {
      Bits live = ctx.reader().getLiveDocs();
      for (int d = 0; d < ctx.reader().maxDoc(); d++) {
        if (live == null || live.get(d)) {
          ids.add(ctx.reader().storedFields().document(d).get("id"));
        }
      }
    }
    return ids;
  }

  private static String describe(DirectoryReader r) throws Exception {
    return "liveIds=" + liveIds(r) + " numDocs=" + r.numDocs() + " maxDoc=" + r.maxDoc();
  }

  private static void addABCAndCommit(IndexWriter w) throws Exception {
    w.addDocument(doc("a"));
    w.addDocument(doc("b"));
    w.addDocument(doc("c"));
    w.commit();
  }

  private static String commitInfo(Directory dir) throws Exception {
    StringBuilder sb = new StringBuilder();
    for (SegmentCommitInfo sci : SegmentInfos.readLatestCommit(dir)) {
      sb.append(sci.info.name)
          .append(" maxDoc=")
          .append(sci.info.maxDoc())
          .append(" delCount=")
          .append(sci.getDelCount())
          .append(" softDelCount=")
          .append(sci.getSoftDelCount())
          .append("; ");
    }
    return sb.toString();
  }

  private static boolean checkIndexClean(Directory dir) throws Exception {
    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
    try (CheckIndex ci = new CheckIndex(dir)) {
      ci.setLevel(CheckIndex.Level.MIN_LEVEL_FOR_SLOW_CHECKS);
      ci.setInfoStream(new java.io.PrintStream(bos, true, "UTF-8"));
      CheckIndex.Status st = ci.checkIndex();
      if (st.clean == false) {
        for (String line : bos.toString("UTF-8").split("\n")) {
          if (line.contains("ERROR") || line.contains("FAILED") || line.contains("WARNING")) {
            System.out.println("  CheckIndex: " + line.trim());
          }
        }
      }
      return st.clean;
    }
  }

  // ---------------------------------------------------------------- controls (expected to pass)

  /** Control 1: documented un-delete via tryUpdateDocValue(null) of a soft-deleted doc works. */
  public void testControlReviveSoftDeletedDoc() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, config())) {
      addABCAndCommit(w);
      w.updateDocValues(new Term("id", "b"), new NumericDocValuesField(SD, 1));
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertEquals(List.of("a", "c"), liveIds(r));
        assertTrue(w.tryUpdateDocValue(r, 1, removeSoft()) > 0);
      }
      try (DirectoryReader r2 = DirectoryReader.open(w)) {
        System.out.println("control1 " + describe(r2));
        assertEquals(List.of("a", "b", "c"), liveIds(r2));
        assertEquals(3, r2.numDocs());
      }
      w.commit();
      w.close();
      assertTrue(checkIndexClean(dir));
    }
  }

  /** Control 2: hard delete, then tryUpdateDocValue that SETS a soft-deletes value: consistent. */
  public void testControlSetSoftValueOnHardDeletedDoc() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, config())) {
      addABCAndCommit(w);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        w.deleteDocuments(new Term("id", "a"));
        assertTrue(w.tryUpdateDocValue(r, 0, new NumericDocValuesField(SD, 1)) > 0);
      }
      try (DirectoryReader r2 = DirectoryReader.open(w)) {
        System.out.println("control2 " + describe(r2));
        assertEquals(List.of("b", "c"), liveIds(r2));
        assertEquals(2, r2.numDocs());
      }
      w.commit();
      w.close();
      assertTrue(checkIndexClean(dir));
    }
  }

  /** Control 3: remove-soft-value then hard delete, with an NRT reader in between: consistent. */
  public void testControlRemoveSoftValueWrittenBeforeHardDelete() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, config())) {
      addABCAndCommit(w);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertTrue(w.tryUpdateDocValue(r, 0, removeSoft()) > 0);
      }
      // the DV update is written (applied to the live bits) when this reader is opened
      DirectoryReader.open(w).close();
      w.deleteDocuments(new Term("id", "a"));
      try (DirectoryReader r2 = DirectoryReader.open(w)) {
        System.out.println("control3 " + describe(r2));
        assertEquals(List.of("b", "c"), liveIds(r2));
      }
      w.commit();
      w.close();
      assertTrue(checkIndexClean(dir));
    }
  }

  // ---------------------------------------------------------------- candidate bug

  /**
   * T1: doc a hard-deleted, then tryUpdateDocValue(r, a, soft=null) (a was never soft-deleted).
   * Expected: a stays deleted. With -ea: assertion "illegal pending delete count: -1" expected if
   * buggy.
   */
  public void testRemoveSoftValueOfHardDeletedDoc() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, config())) {
      addABCAndCommit(w);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        w.deleteDocuments(new Term("id", "a"));
        long seq = w.tryUpdateDocValue(r, 0, removeSoft());
        System.out.println("T1 tryUpdateDocValue seq=" + seq);
      }
      try (DirectoryReader r2 = DirectoryReader.open(w)) {
        System.out.println("T1 " + describe(r2));
        assertEquals(List.of("b", "c"), liveIds(r2));
        assertEquals(2, r2.numDocs());
      }
    }
  }

  /**
   * T2: same as T1, but doc b is soft-deleted first, so the soft-delete counter has slack and the
   * accounting does not go negative. Expected live = [c]. If buggy: hard-deleted a comes back.
   * Then commit and compare the on-disk view.
   */
  public void testSilentResurrectionOfHardDeletedDoc() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, config())) {
        addABCAndCommit(w);
        w.updateDocValues(new Term("id", "b"), new NumericDocValuesField(SD, 1)); // soft-delete b
        try (DirectoryReader r = DirectoryReader.open(w)) {
          assertEquals(List.of("a", "c"), liveIds(r));
          w.deleteDocuments(new Term("id", "a")); // hard-delete a
          assertTrue(w.tryUpdateDocValue(r, 0, removeSoft()) > 0); // remove a's (absent) soft value
        }
        String nrt;
        long hitsForA;
        try (DirectoryReader r2 = DirectoryReader.open(w)) {
          nrt = describe(r2);
          hitsForA = new IndexSearcher(r2).count(new TermQuery(new Term("id", "a")));
          System.out.println("T2 NRT " + nrt + " count(id:a)=" + hitsForA);
        }
        w.commit();
        System.out.println("T2 commit: " + commitInfo(dir));
        try (DirectoryReader c = DirectoryReader.open(dir)) {
          System.out.println("T2 DirectoryReader.open(dir) " + describe(c));
        }
        w.close();
        boolean clean = checkIndexClean(dir);
        System.out.println("T2 CheckIndex clean=" + clean);
        assertEquals("NRT reader after hard delete of a + soft delete of b", "liveIds=[c] numDocs=1 maxDoc=3", nrt);
        assertEquals(0, hitsForA);
        assertTrue("CheckIndex", clean);
      }
    }
  }

  /**
   * T3: the documented un-delete use of tryUpdateDocValue (IndexWriter javadoc), followed by a hard
   * delete of the same doc before the DV update has been written. Expected live = [a, c].
   */
  public void testReviveThenHardDeleteSameDoc() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, config())) {
      addABCAndCommit(w);
      w.updateDocValues(new Term("id", "b"), new NumericDocValuesField(SD, 1)); // soft-delete b
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertEquals(List.of("a", "c"), liveIds(r));
        assertTrue(w.tryUpdateDocValue(r, 1, removeSoft()) > 0); // revive b (documented)
        w.deleteDocuments(new Term("id", "b")); // then hard-delete b
      }
      try (DirectoryReader r2 = DirectoryReader.open(w)) {
        System.out.println("T3 " + describe(r2));
        assertEquals(List.of("a", "c"), liveIds(r2));
        assertEquals(2, r2.numDocs());
      }
    }
  }

  /**
   * T4 (control-like): term-based API only: updateDocValues(id:a, soft=null) on a LIVE committed doc
   * (semantically a no-op), then deleteDocuments(id:a), both buffered in the same global packet.
   * Expected live = [b, c].
   */
  public void testTermRemoveSoftValueThenHardDelete() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, config())) {
      addABCAndCommit(w);
      w.updateDocValues(new Term("id", "a"), removeSoft()); // no-op on a live doc
      w.deleteDocuments(new Term("id", "a")); // hard-delete a
      try (DirectoryReader r2 = DirectoryReader.open(w)) {
        System.out.println("T4 " + describe(r2));
        assertEquals(List.of("b", "c"), liveIds(r2));
      }
    }
  }

  /**
   * T4b: term-based API only, but the soft=null update is resolved against an in-RAM segment by an
   * AUTO flush (maxBufferedDocs=3) before the hard delete is applied. Expected live = [b, c].
   */
  public void testTermRemoveSoftValueAutoFlushThenHardDelete() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, config().setMaxBufferedDocs(3).setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH))) {
      w.addDocument(doc("a"));
      w.addDocument(doc("b"));
      w.updateDocValues(new Term("id", "a"), removeSoft()); // no-op on a live doc
      w.addDocument(doc("c")); // 3rd buffered doc -> auto flush
      w.deleteDocuments(new Term("id", "a")); // hard-delete a
      try (DirectoryReader r2 = DirectoryReader.open(w)) {
        System.out.println("T4b " + describe(r2));
        assertEquals(List.of("b", "c"), liveIds(r2));
      }
    }
  }

  /**
   * T5: after the T1 sequence (hard delete + tryUpdateDocValue(soft=null)), is the writer usable? Every getReader/commit/close is attempted; data
   * added after the bad op must survive a commit.
   */
  public void testWriterAftermath() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w = new IndexWriter(dir, config());
      addABCAndCommit(w);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        w.deleteDocuments(new Term("id", "a"));
        w.tryUpdateDocValue(r, 0, removeSoft());
      }
      w.addDocument(doc("d")); // new data written after the bad op
      List<String> failures = new ArrayList<>();
      for (int i = 0; i < 2; i++) {
        try {
          DirectoryReader.open(w).close();
          System.out.println("T5 open#" + i + " ok");
        } catch (Throwable t) {
          System.out.println("T5 open#" + i + " -> " + t);
          failures.add("open#" + i + ": " + t);
        }
      }
      try {
        w.commit();
        System.out.println("T5 commit ok");
      } catch (Throwable t) {
        System.out.println("T5 commit -> " + t);
        failures.add("commit: " + t);
      }
      try {
        w.close();
        System.out.println("T5 close ok");
      } catch (Throwable t) {
        System.out.println("T5 close -> " + t);
        failures.add("close: " + t);
        if (w.isOpen()) {
          w.rollback();
        }
      }
      try (DirectoryReader c = DirectoryReader.open(dir)) {
        System.out.println("T5 committed view " + describe(c));
        if (liveIds(c).contains("d") == false) {
          failures.add("doc d (added after the bad op) not in the last commit");
        }
      }
      assertTrue("writer aftermath failures: " + failures, failures.isEmpty());
    }
  }
}
