import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check of candidate R5: after the writer that produced an NRT reader discards its
 * uncommitted changes (rollback(), or close() with commitOnClose=false), a NEW writer restarts
 * SegmentInfos.version from the last commit. Since the orphaned reader's isCurrent() only compares
 * versions against the latest commit once its writer is closed, does it report "current" when a
 * later commit happens to land on the same version number with different content?
 *
 * <p>Fixed, non-adaptive sequence: W1 = 3 x (add + NRT reopen); W2 = 4 x (add + commit). After
 * every W2 commit we record (version, segments file, isCurrent, contents equal?) and at the end
 * assert isCurrent() ==> same content for every row.
 */
public class TestOrphanedNrtIsCurrentAfterDiscard extends LuceneTestCase {

  enum Discard {
    ROLLBACK,
    CLOSE_NO_COMMIT,
    CLOSE_WITH_COMMIT /* control */
  }

  private static Document doc(String id) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    return d;
  }

  private static TreeSet<String> ids(DirectoryReader r) throws IOException {
    TreeSet<String> out = new TreeSet<>();
    StoredFields sf = r.storedFields();
    for (int i = 0; i < r.maxDoc(); i++) {
      out.add(sf.document(i).get("id"));
    }
    return out;
  }

  private static IndexWriterConfig cfg(boolean commitOnClose) {
    // no merges, no randomized IWC, so the version arithmetic is reproducible
    return new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE).setCommitOnClose(commitOnClose);
  }

  /** Returns rows "W2 commit i: ..." where isCurrent()==true but content differs (violations). */
  private List<String> run(Directory dir, Discard mode) throws IOException {
    IndexWriter w1 = new IndexWriter(dir, cfg(mode != Discard.CLOSE_NO_COMMIT));
    w1.addDocument(doc("A"));
    w1.commit();
    long c = SegmentInfos.readLatestCommit(dir).getVersion();
    DirectoryReader r = DirectoryReader.open(w1);
    System.out.println(mode + " " + dir.getClass().getSimpleName() + ": commit#1 version=" + c
        + " ; NRT open version=" + r.getVersion());
    for (int i = 1; i <= 3; i++) {
      w1.addDocument(doc("W1-" + i));
      DirectoryReader r2 = DirectoryReader.openIfChanged(r, w1);
      assertNotNull(r2);
      r.close();
      r = r2;
      System.out.println("  W1 add+reopen #" + i + " -> NRT version=" + r.getVersion());
    }
    final long nrtVersion = r.getVersion();
    final TreeSet<String> nrtIds = ids(r);
    assertTrue(r.isCurrent());

    switch (mode) {
      case ROLLBACK -> w1.rollback();
      case CLOSE_NO_COMMIT, CLOSE_WITH_COMMIT -> w1.close();
    }
    SegmentInfos afterW1 = SegmentInfos.readLatestCommit(dir);
    System.out.println("  after W1 " + mode + ": latest commit " + afterW1.getSegmentsFileName()
        + " version=" + afterW1.getVersion() + " ; orphan isCurrent=" + r.isCurrent());
    // documented: an orphaned NRT reader cannot be reopened
    final DirectoryReader orphan = r;
    expectThrows(AlreadyClosedException.class, () -> DirectoryReader.openIfChanged(orphan));

    List<String> violations = new ArrayList<>();
    IndexWriter w2 = new IndexWriter(dir, cfg(true));
    try {
      for (int i = 1; i <= 4; i++) {
        w2.addDocument(doc("W2-" + i));
        w2.commit();
        try (DirectoryReader fresh = DirectoryReader.open(dir)) {
          boolean current = r.isCurrent();
          boolean sameContent = ids(fresh).equals(nrtIds);
          String row = String.format(java.util.Locale.ROOT,
              "  W2 add+commit #%d: latest=%s version=%d | orphan NRT version=%d segFile=%s ids=%s"
                  + " | fresh ids=%s | orphan.isCurrent()=%s sameContent=%s",
              i, fresh.getIndexCommit().getSegmentsFileName(), fresh.getVersion(), nrtVersion,
              r.getIndexCommit().getSegmentsFileName(), nrtIds, ids(fresh), current, sameContent);
          System.out.println(row + (current && !sameContent ? "   <-- VIOLATION" : ""));
          if (current && !sameContent) {
            violations.add(row.trim());
          }
        }
      }
    } finally {
      w2.close();
      r.close();
    }
    return violations;
  }

  private void check(Directory dir, Discard mode) throws IOException {
    List<String> v = run(dir, mode);
    assertTrue(
        mode + ": orphaned NRT reader isCurrent()==true although a new commit with different"
            + " content exists: " + v,
        v.isEmpty());
  }

  public void testRollback_ByteBuffers() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      check(dir, Discard.ROLLBACK);
    }
  }

  public void testRollback_FSDirectory() throws IOException {
    try (Directory dir = FSDirectory.open(createTempDir("r5"))) {
      check(dir, Discard.ROLLBACK);
    }
  }

  public void testCloseWithoutCommit_ByteBuffers() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      check(dir, Discard.CLOSE_NO_COMMIT);
    }
  }

  /** Control: W1 commits on close, so W2 starts above every version the NRT reader saw. */
  public void testControl_CloseWithCommit_ByteBuffers() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      check(dir, Discard.CLOSE_WITH_COMMIT);
    }
  }
}
