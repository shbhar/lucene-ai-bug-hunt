import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.SegmentCommitInfo;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.index.SegmentReader;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.StringHelper;

/**
 * Independent verification of candidate "L": segment names of rolled-back segments are reused, and
 * an old NRT reader from the rolled-back writer then cannot be reopened against the new commit.
 *
 * <p>All configs are plain {@code new IndexWriterConfig()} (not the randomized test-framework one),
 * NoMergePolicy, so every test is deterministic regardless of tests.seed.
 */
@LuceneTestCase.SuppressSysoutChecks(bugUrl = "prints diagnostics")
public class TestRolledBackSegmentNameReuse extends LuceneTestCase {

  private static IndexWriterConfig iwc() {
    return new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
  }

  private static Document doc(String id) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    return d;
  }

  private static String describe(DirectoryReader r) {
    List<String> out = new ArrayList<>();
    for (LeafReaderContext ctx : r.leaves()) {
      SegmentReader sr = (SegmentReader) ctx.reader();
      out.add(
          sr.getSegmentName()
              + "(maxDoc="
              + sr.maxDoc()
              + ",id="
              + StringHelper.idToString(sr.getSegmentInfo().info.getId())
              + ")");
    }
    return out.toString();
  }

  private static String describe(SegmentInfos sis) {
    List<String> out = new ArrayList<>();
    for (SegmentCommitInfo sci : sis) {
      out.add(
          sci.info.name
              + "(maxDoc="
              + sci.info.maxDoc()
              + ",id="
              + StringHelper.idToString(sci.info.getId())
              + ")");
    }
    return out + " counter=" + sis.counter + " gen=" + sis.getGeneration();
  }

  /** Reopen r against the latest commit; returns a printable outcome. */
  private static String reopenOutcome(DirectoryReader r, Directory dir) {
    try {
      DirectoryReader r2 = DirectoryReader.openIfChanged(r, DirectoryReader.listCommits(dir).get(0));
      if (r2 == null) {
        return "null";
      }
      String s = "ok numDocs=" + r2.numDocs();
      r2.close();
      return s;
    } catch (Throwable t) {
      return t.getClass().getSimpleName() + ": " + t.getMessage();
    }
  }

  /**
   * The candidate's scenario, on a never-committed index: rollback w1 (NRT reader r holds _0
   * open), open+rollback w2 (its IndexFileDeleter deletes the leftover _0 files), w3 commits a new
   * _0. Encodes the candidate's EXPECTATION, so it FAILS if the behaviour reproduces.
   */
  private void scenarioNeverCommitted(Directory dir, String label) throws Exception {
    IndexWriter w1 = new IndexWriter(dir, iwc());
    w1.addDocument(doc("rolled-back"));
    DirectoryReader r = DirectoryReader.open(w1);
    String rDesc = describe(r);
    w1.rollback();
    String[] afterW1Rollback = dir.listAll();

    IndexWriter w2 = new IndexWriter(dir, iwc());
    w2.rollback();
    String[] afterW2Rollback = dir.listAll();

    IndexWriter w3 = new IndexWriter(dir, iwc());
    w3.addDocument(doc("new-0"));
    w3.addDocument(doc("new-1"));
    w3.commit();
    w3.close();
    SegmentInfos newCommit = SegmentInfos.readLatestCommit(dir);

    String outcome = reopenOutcome(r, dir);
    String oldOwnRead = r.storedFields().document(0).get("id");
    DirectoryReader fresh = DirectoryReader.open(dir);
    int freshNumDocs = fresh.numDocs();
    fresh.close();

    System.out.println("[" + label + "] old NRT reader r          = " + rDesc);
    System.out.println("[" + label + "] files after w1.rollback() = " + String.join(",", afterW1Rollback));
    System.out.println("[" + label + "] files after w2.rollback() = " + String.join(",", afterW2Rollback));
    System.out.println("[" + label + "] new commit (w3)           = " + describe(newCommit));
    System.out.println("[" + label + "] openIfChanged(r, newCommit) -> " + outcome);
    System.out.println("[" + label + "] r still reads its own doc0 id=" + oldOwnRead
        + " ; fresh DirectoryReader.open(dir).numDocs=" + freshNumDocs);
    r.close();

    assertEquals("old reader keeps reading its own (deleted-but-open) files", "rolled-back", oldOwnRead);
    assertEquals("fresh reader sees new commit", 2, freshNumDocs);
    // Candidate's expectation:
    assertEquals(label + ": candidate expects a successful reopen", "ok numDocs=2", outcome);
  }

  public void testCandidateScenarioByteBuffers() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      scenarioNeverCommitted(dir, "never-committed/ByteBuffers");
    }
  }

  public void testCandidateScenarioNIOFS() throws Exception {
    try (Directory dir = new NIOFSDirectory(createTempDir("l-niofs"))) {
      scenarioNeverCommitted(dir, "never-committed/NIOFS");
    }
  }

  /**
   * Same scenario but on top of an existing commit c1 = [_0]. w1 flushes _1 for its NRT reader, is
   * rolled back; w2 open+rollback; w3 (opened on c1, counter=1) commits a different _1. Encodes the
   * candidate's expectation (FAILS if the behaviour reproduces); shows the issue is not specific to
   * never-committed indexes.
   */
  public void testCommittedBaseVariant() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w0 = new IndexWriter(dir, iwc());
      w0.addDocument(doc("committed"));
      w0.close(); // commit c1 = [_0]

      IndexWriter w1 = new IndexWriter(dir, iwc());
      w1.addDocument(doc("rolled-back"));
      DirectoryReader r = DirectoryReader.open(w1); // [_0, _1]
      String rDesc = describe(r);
      w1.rollback();
      new IndexWriter(dir, iwc()).rollback();
      IndexWriter w3 = new IndexWriter(dir, iwc());
      w3.addDocument(doc("new-a"));
      w3.addDocument(doc("new-b"));
      w3.commit();
      w3.close();
      SegmentInfos newCommit = SegmentInfos.readLatestCommit(dir);
      String outcome = reopenOutcome(r, dir);
      System.out.println("[committed-base] old NRT reader r = " + rDesc);
      System.out.println("[committed-base] new commit       = " + describe(newCommit));
      System.out.println("[committed-base] openIfChanged(r, newCommit) -> " + outcome);
      r.close();
      assertEquals("committed-base: candidate expects a successful reopen", "ok numDocs=3", outcome);
    }
  }

  /**
   * CONTROL: only ONE rollback. The NRT reader's refs keep _0's files on disk after w1.rollback(),
   * so w3's IndexFileDeleter.inflateGens bumps the counter past _0, w3 writes _1, and the reopen
   * against the new commit works. Expected to PASS.
   */
  public void testControlSingleRollbackInflatesCounter() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w1 = new IndexWriter(dir, iwc());
      w1.addDocument(doc("rolled-back"));
      DirectoryReader r = DirectoryReader.open(w1);
      w1.rollback();
      IndexWriter w3 = new IndexWriter(dir, iwc());
      w3.addDocument(doc("new-0"));
      w3.addDocument(doc("new-1"));
      w3.commit();
      w3.close();
      SegmentInfos newCommit = SegmentInfos.readLatestCommit(dir);
      String outcome = reopenOutcome(r, dir);
      System.out.println("[control-1-rollback] old r=" + describe(r) + " new commit=" + describe(newCommit)
          + " -> openIfChanged(r,newCommit)=" + outcome);
      r.close();
      assertEquals("_1", newCommit.info(0).info.name);
      assertEquals("ok numDocs=2", outcome);
    }
  }

  /**
   * CONTROL: name reuse after rollback is the normal, by-design outcome when nobody holds the
   * rolled-back segment: rollback restores the index to the last commit (including its segment
   * counter), so the next writer re-issues the rolled-back name. Expected to PASS.
   */
  public void testControlNameReuseAfterPlainRollbackIsInherent() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w0 = new IndexWriter(dir, iwc());
      w0.addDocument(doc("committed"));
      w0.close(); // c1 = [_0], counter=1

      IndexWriter w1 = new IndexWriter(dir, iwc());
      w1.addDocument(doc("rolled-back"));
      String rolledBackName;
      byte[] rolledBackId;
      try (DirectoryReader r = DirectoryReader.open(w1)) { // forces a flush of _1
        SegmentReader sr = (SegmentReader) r.leaves().get(1).reader();
        rolledBackName = sr.getSegmentName();
        rolledBackId = sr.getSegmentInfo().info.getId();
      } // reader closed BEFORE rollback, so rollback deletes _1's files itself
      w1.rollback();

      IndexWriter w2 = new IndexWriter(dir, iwc());
      w2.addDocument(doc("next"));
      w2.close();
      SegmentInfos c2 = SegmentInfos.readLatestCommit(dir);
      System.out.println("[control-plain-rollback] w1 flushed+rolled back " + rolledBackName
          + "(id=" + StringHelper.idToString(rolledBackId) + "); next commit = " + describe(c2));
      assertEquals(rolledBackName, c2.info(1).info.name); // same name re-issued ...
      assertFalse(java.util.Arrays.equals(rolledBackId, c2.info(1).info.getId())); // ... new id
      try (DirectoryReader fresh = DirectoryReader.open(dir)) {
        assertEquals(2, fresh.numDocs());
      }
    }
  }

  /**
   * The ISE message advises "open a new IndexWriter using OpenMode.CREATE instead". Show that with
   * w3 opened in OpenMode.CREATE the name is reused all the same (no prior commit to inherit a
   * counter from). Asserts OBSERVED behaviour; PASSES if the advice does not help.
   */
  public void testMessageAdviceOpenModeCreateDoesNotHelp() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w1 = new IndexWriter(dir, iwc());
      w1.addDocument(doc("rolled-back"));
      DirectoryReader r = DirectoryReader.open(w1);
      w1.rollback();
      new IndexWriter(dir, iwc()).rollback();
      IndexWriter w3 = new IndexWriter(dir, iwc().setOpenMode(IndexWriterConfig.OpenMode.CREATE));
      w3.addDocument(doc("new-0"));
      w3.addDocument(doc("new-1"));
      w3.commit();
      w3.close();
      SegmentInfos newCommit = SegmentInfos.readLatestCommit(dir);
      String outcome = reopenOutcome(r, dir);
      System.out.println("[advice-OpenMode.CREATE] new commit=" + describe(newCommit)
          + " -> openIfChanged(r,newCommit)=" + outcome);
      r.close();
      assertEquals("_0", newCommit.info(0).info.name);
      assertTrue(outcome, outcome.startsWith("IllegalStateException"));
    }
  }

  /**
   * CONTROL: if the intermediate writer w2 is CLOSED (commits, persisting its inflated counter)
   * instead of rolled back, there is no reuse and the reopen works. Expected to PASS.
   */
  public void testControlSecondWriterClosedPersistsCounter() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w1 = new IndexWriter(dir, iwc());
      w1.addDocument(doc("rolled-back"));
      DirectoryReader r = DirectoryReader.open(w1);
      w1.rollback();
      new IndexWriter(dir, iwc()).close(); // commits an empty segments_1 with counter=1
      IndexWriter w3 = new IndexWriter(dir, iwc());
      w3.addDocument(doc("new-0"));
      w3.addDocument(doc("new-1"));
      w3.commit();
      w3.close();
      SegmentInfos newCommit = SegmentInfos.readLatestCommit(dir);
      String outcome = reopenOutcome(r, dir);
      System.out.println("[control-w2-closed] new commit=" + describe(newCommit)
          + " -> openIfChanged(r,newCommit)=" + outcome);
      r.close();
      assertEquals("_1", newCommit.info(0).info.name);
      assertEquals("ok numDocs=2", outcome);
    }
  }

  /**
   * CONTROL: the documented contract (DirectoryReader#openIfChanged(DirectoryReader, IndexWriter)
   * javadoc: "Once the writer is closed, any outstanding readers may continue to be used. However,
   * if you attempt to reopen any of those readers, you'll hit an AlreadyClosedException").
   */
  public void testControlDocumentedReopenAfterWriterCloseThrowsACE() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w1 = new IndexWriter(dir, iwc());
      w1.addDocument(doc("rolled-back"));
      DirectoryReader r = DirectoryReader.open(w1);
      w1.rollback();
      expectThrows(AlreadyClosedException.class, () -> DirectoryReader.openIfChanged(r));
      expectThrows(AlreadyClosedException.class, () -> DirectoryReader.openIfChanged(r, w1));
      assertEquals("rolled-back", r.storedFields().document(0).get("id")); // still usable
      System.out.println("[control-documented-ACE] openIfChanged(r) and openIfChanged(r,w1) after rollback -> AlreadyClosedException (as documented)");
      r.close();
    }
  }
}
