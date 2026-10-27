import org.apache.lucene.document.BinaryDocValuesField;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * Independent verification of candidate "mbt-randomized A".
 *
 * <p>Claim: IndexWriter#tryUpdateDocValue (later seqNo) loses against an EARLIER term-based
 * doc-values update that is still sitting in the writer's global (unfrozen) delete/update buffer.
 *
 * <p>Every test uses a plain IndexWriterConfig + ByteBuffersDirectory + NoMergePolicy so the only
 * randomness from LuceneTestCase is irrelevant (no random IWC, no MockDirectoryWrapper): the
 * outcome is deterministic and seed-independent.
 *
 * <p>Index layout in every test: one flushed segment with doc 0 = id:1 (n=1, b="orig"), doc 1 =
 * id:2. The NRT reader {@code r} is opened right after indexing, so doc 0 is visible to it.
 */
public class TestTryUpdateDocValueVsBufferedTermUpdate extends LuceneTestCase {

  private static Document doc(String id) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    d.add(new NumericDocValuesField("n", 1));
    d.add(new BinaryDocValuesField("b", new BytesRef("orig")));
    return d;
  }

  private static IndexWriterConfig iwc(boolean softDeletes) {
    IndexWriterConfig c = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
    if (softDeletes) {
      c.setSoftDeletesField("sd");
    }
    return c;
  }

  /** value of numeric field n for doc 0 of the (single-segment) reader */
  private static long n0(DirectoryReader r) throws Exception {
    assertEquals(1, r.leaves().size());
    LeafReader leaf = r.leaves().get(0).reader();
    NumericDocValues n = leaf.getNumericDocValues("n");
    assertTrue(n.advanceExact(0));
    return n.longValue();
  }

  private static String b0(DirectoryReader r) throws Exception {
    assertEquals(1, r.leaves().size());
    BinaryDocValues b = r.leaves().get(0).reader().getBinaryDocValues("b");
    assertTrue(b.advanceExact(0));
    return b.binaryValue().utf8ToString();
  }

  /**
   * BUG CANDIDATE (numeric): term update (n=2, seq s1) is buffered, then tryUpdateDocValue (n=3,
   * seq s2 > s1). Check fresh NRT reader AND the commit.
   */
  public void testNumericTermUpdateThenTryUpdate() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc(false))) {
      w.addDocument(doc("1"));
      w.addDocument(doc("2"));
      try (DirectoryReader r = DirectoryReader.open(w)) {
        long s1 = w.updateDocValues(new Term("id", "1"), new NumericDocValuesField("n", 2));
        long s2 = w.tryUpdateDocValue(r, 0, new NumericDocValuesField("n", 3));
        System.out.println("[numeric] termUpdate seq=" + s1 + " tryUpdate seq=" + s2);
        assertTrue("tryUpdateDocValue must succeed", s2 != -1);
        assertTrue("seq order", s2 > s1);
        long nrt;
        try (DirectoryReader fresh = DirectoryReader.open(w)) {
          nrt = n0(fresh);
        }
        w.commit();
        long committed;
        try (DirectoryReader c = DirectoryReader.open(dir)) {
          committed = n0(c);
        }
        // structural integrity of the commit (no corruption, just the wrong value):
        org.apache.lucene.tests.util.TestUtil.checkIndex(dir);
        System.out.println("[numeric] CheckIndex on the commit: clean");
        System.out.println("[numeric] fresh NRT n=" + nrt + " committed n=" + committed);
        assertEquals("fresh NRT reader: later op (tryUpdate, n=3) must win", 3, nrt);
        assertEquals("commit: later op (tryUpdate, n=3) must win", 3, committed);
      }
    }
  }

  /** BUG CANDIDATE (binary). */
  public void testBinaryTermUpdateThenTryUpdate() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc(false))) {
      w.addDocument(doc("1"));
      w.addDocument(doc("2"));
      try (DirectoryReader r = DirectoryReader.open(w)) {
        long s1 =
            w.updateDocValues(new Term("id", "1"), new BinaryDocValuesField("b", new BytesRef("T")));
        long s2 = w.tryUpdateDocValue(r, 0, new BinaryDocValuesField("b", new BytesRef("TRY")));
        System.out.println("[binary] termUpdate seq=" + s1 + " tryUpdate seq=" + s2);
        assertTrue(s2 != -1 && s2 > s1);
        String nrt;
        try (DirectoryReader fresh = DirectoryReader.open(w)) {
          nrt = b0(fresh);
        }
        System.out.println("[binary] fresh NRT b=" + nrt);
        assertEquals("later op (tryUpdate) must win", "TRY", nrt);
      }
    }
  }

  /**
   * BUG CANDIDATE (soft deletes): soft-delete via term DV update (sd=1), then tryUpdateDocValue
   * with sd=null ("can be used to un-delete a soft-deleted document" per its javadoc).
   */
  public void testSoftDeleteByTermThenTryUndelete() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc(true))) {
      w.addDocument(doc("1"));
      w.addDocument(doc("2"));
      try (DirectoryReader r = DirectoryReader.open(w)) {
        long s1 = w.updateDocValues(new Term("id", "1"), new NumericDocValuesField("sd", 1));
        long s2 = w.tryUpdateDocValue(r, 0, new NumericDocValuesField("sd", (Long) null));
        System.out.println("[soft] softDelete seq=" + s1 + " tryUndelete seq=" + s2);
        assertTrue(s2 != -1 && s2 > s1);
        int numDocs;
        try (DirectoryReader fresh = DirectoryReader.open(w)) {
          numDocs = fresh.numDocs();
        }
        System.out.println("[soft] fresh NRT numDocs=" + numDocs);
        assertEquals("later op (un-delete) must win: both docs live", 2, numDocs);
      }
    }
  }

  // ---------------------------------------------------------------- controls (expected to pass)

  /** CONTROL 1: same as the numeric bug test but with an explicit flush() between the two ops. */
  public void testControlFlushBetween() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc(false))) {
      w.addDocument(doc("1"));
      w.addDocument(doc("2"));
      try (DirectoryReader r = DirectoryReader.open(w)) {
        w.updateDocValues(new Term("id", "1"), new NumericDocValuesField("n", 2));
        w.flush(); // freezes + applies the buffered term update
        assertTrue(w.tryUpdateDocValue(r, 0, new NumericDocValuesField("n", 3)) != -1);
        try (DirectoryReader fresh = DirectoryReader.open(w)) {
          assertEquals(3, n0(fresh));
        }
      }
    }
  }

  /**
   * CONTROL 2: the buffered term update is only FROZEN (pushed to BufferedUpdatesStream, not yet
   * applied) by an NRT open with applyAllDeletes=false; tryUpdateDocValue then wins.
   */
  public void testControlFrozenButUnappliedBetween() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc(false))) {
      w.addDocument(doc("1"));
      w.addDocument(doc("2"));
      try (DirectoryReader r = DirectoryReader.open(w)) {
        w.updateDocValues(new Term("id", "1"), new NumericDocValuesField("n", 2));
        DirectoryReader.open(w, false, false).close();
        assertTrue(w.tryUpdateDocValue(r, 0, new NumericDocValuesField("n", 3)) != -1);
        try (DirectoryReader fresh = DirectoryReader.open(w)) {
          assertEquals(3, n0(fresh));
        }
      }
    }
  }

  /** CONTROL 3: reverse order (tryUpdate first, then term update): the term update must win. */
  public void testControlReverseOrder() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc(false))) {
      w.addDocument(doc("1"));
      w.addDocument(doc("2"));
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertTrue(w.tryUpdateDocValue(r, 0, new NumericDocValuesField("n", 3)) != -1);
        w.updateDocValues(new Term("id", "1"), new NumericDocValuesField("n", 2));
        try (DirectoryReader fresh = DirectoryReader.open(w)) {
          assertEquals(2, n0(fresh));
        }
      }
    }
  }

  /** CONTROL 4: two buffered term updates: last one wins. */
  public void testControlTwoTermUpdates() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc(false))) {
      w.addDocument(doc("1"));
      w.addDocument(doc("2"));
      DirectoryReader.open(w).close();
      w.updateDocValues(new Term("id", "1"), new NumericDocValuesField("n", 2));
      w.updateDocValues(new Term("id", "1"), new NumericDocValuesField("n", 3));
      try (DirectoryReader fresh = DirectoryReader.open(w)) {
        assertEquals(3, n0(fresh));
      }
    }
  }

  /** CONTROL 5: two tryUpdateDocValue calls: last one wins. */
  public void testControlTwoTryUpdates() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc(false))) {
      w.addDocument(doc("1"));
      w.addDocument(doc("2"));
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertTrue(w.tryUpdateDocValue(r, 0, new NumericDocValuesField("n", 2)) != -1);
        assertTrue(w.tryUpdateDocValue(r, 0, new NumericDocValuesField("n", 3)) != -1);
        try (DirectoryReader fresh = DirectoryReader.open(w)) {
          assertEquals(3, n0(fresh));
        }
      }
    }
  }
}
