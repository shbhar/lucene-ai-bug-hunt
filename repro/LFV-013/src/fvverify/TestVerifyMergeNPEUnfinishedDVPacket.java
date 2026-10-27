/*
 * Independent verification test.
 * Written from scratch; uses only public Lucene API (package fvverify, not org.apache.lucene.index).
 *
 * Claim under test: a merge whose mergeInit writes pending doc-values updates of a segment while
 * another thread has APPLIED but not yet FINISHED a packet carrying a DV update for a field that the
 * segment does not have yet, records that field in the new .fnm generation with a DV type but
 * dvGen=-1 (no values). If no merging segment has values for that field, DocIDMerger.next() NPEs and
 * IndexWriter closes itself with a tragic exception.
 *
 * Every test asserts the CORRECT (expected) behaviour, so a failing "bug" test = bug reproduced, and
 * the controls must pass.
 */
package fvverify;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.document.BinaryDocValuesField;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.CheckIndex;
import org.apache.lucene.index.CodecReader;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocValuesType;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FilterMergePolicy;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.MergePolicy;
import org.apache.lucene.index.MergeTrigger;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.SegmentCommitInfo;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.InfoStream;

public class TestVerifyMergeNPEUnfinishedDVPacket extends LuceneTestCase {

  /** Merges segments named in {@code names} once, when armed. Never merges otherwise. */
  static final class OneShotMergePolicy extends FilterMergePolicy {
    volatile List<String> names;

    OneShotMergePolicy() {
      super(NoMergePolicy.INSTANCE);
    }

    @Override
    public MergeSpecification findMerges(
        MergeTrigger trigger, SegmentInfos infos, MergeContext ctx) throws IOException {
      List<String> want = names;
      if (want == null) {
        return null;
      }
      names = null;
      List<SegmentCommitInfo> picked = new ArrayList<>();
      for (SegmentCommitInfo sci : infos) {
        if (want.contains(sci.info.name)) {
          picked.add(sci);
        }
      }
      if (picked.size() != want.size()) {
        throw new AssertionError("expected segments " + want + " in " + infos);
      }
      MergeSpecification spec = new MergeSpecification();
      spec.add(new OneMerge(picked));
      return spec;
    }
  }

  /**
   * Blocks the thread {@code victim} the first time it logs a BD message starting with {@code
   * prefix}. Records all BD/IW messages of all threads for diagnostics.
   */
  static final class BlockingInfoStream extends InfoStream {
    final String prefix;
    volatile Thread victim;
    final AtomicBoolean done = new AtomicBoolean();
    final CountDownLatch reached = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);
    final StringBuffer trace = new StringBuffer();

    BlockingInfoStream(String prefix) {
      this.prefix = prefix;
    }

    @Override
    public void message(String component, String message) {
      Thread t = Thread.currentThread();
      trace.append('[').append(t.getName()).append("] ").append(component).append(": ");
      trace.append(message).append('\n');
      if (t == victim && message.startsWith(prefix) && done.compareAndSet(false, true)) {
        reached.countDown();
        try {
          if (release.await(30, TimeUnit.SECONDS) == false) {
            throw new AssertionError("never released");
          }
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
    }

    @Override
    public boolean isEnabled(String component) {
      return "BD".equals(component) || "IW".equals(component);
    }

    @Override
    public void close() {}
  }

  /** What thread B does: one DV update on doc "a" plus one new doc, then flushNextBuffer(). */
  enum Late {
    /** BINARY update of field "tag": only segment _2 has "tag"; _0 and _1 do not. */
    BINARY_NEW_TO_SEGMENT,
    /** NUMERIC update of field "num", which _0 already has (control). */
    NUMERIC_EXISTING_FIELD,
    /** softUpdateDocument: soft-deletes DV update of field "del" (no segment has it yet). */
    SOFT_DELETE
  }

  static final class Outcome {
    Throwable mergeError;
    Throwable tragic;
    boolean open;
    Throwable lateError;
    String trace;
    String fieldInfoOfTagInSeg0DuringWindow; // filled by the NRT-probe variant
    String checkIndexDuringWindow; // filled by the NRT-probe variant
    String finalState; // values seen by a fresh NRT reader after everything settled
    String afterTragic; // state of the Directory after the writer closed itself
  }

  private static Document doc(String id) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    d.add(new NumericDocValuesField("num", 0));
    return d;
  }

  /**
   * @param earlyUpdate whether a finished-but-unwritten DV update on doc a (segment _0) exists
   * @param late what thread B does
   * @param pause whether to hold B between "applied" and "finished" while main merges
   * @param nrtProbe if true, instead of only merging, first open an NRT reader without
   *     applying deletes inside the window and inspect segment _0's FieldInfo/CheckIndex
   */
  private Outcome run(boolean earlyUpdate, Late late, boolean pause, boolean nrtProbe)
      throws Exception {
    return run(earlyUpdate, false, late, pause, nrtProbe);
  }

  /**
   * @param earlyOnTag if true the early (finished, unwritten) update is a BINARY update of "tag"
   *     on a (so "tag" already has an applicable update in _0) instead of a NUMERIC update of
   *     "num"
   */
  private Outcome run(
      boolean earlyUpdate, boolean earlyOnTag, Late late, boolean pause, boolean nrtProbe)
      throws Exception {
    OneShotMergePolicy mp = new OneShotMergePolicy();
    BlockingInfoStream is = new BlockingInfoStream("done inner apply del packet");
    IndexWriterConfig iwc = new IndexWriterConfig();
    if (late == Late.SOFT_DELETE) {
      iwc.setSoftDeletesField("del");
    }
    iwc.setMergePolicy(mp);
    iwc.setMergeScheduler(new SerialMergeScheduler());
    iwc.setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH);
    iwc.setRAMBufferSizeMB(256);
    iwc.setUseCompoundFile(false);
    iwc.setInfoStream(is);
    Outcome out = new Outcome();
    Directory dir = new ByteBuffersDirectory();
    IndexWriter w = new IndexWriter(dir, iwc);
    try {
      w.addDocument(doc("a"));
      w.commit(); // _0 = {a}: fields id, num
      w.addDocument(doc("b"));
      w.commit(); // _1 = {b}: fields id, num
      if (earlyUpdate && earlyOnTag == false) {
        // packet P1 (frozen with _2's flush): numeric update of a in _0. It gets applied and
        // finished by the event processing of flushNextBuffer, but nothing writes it to disk yet.
        w.updateNumericDocValue(new Term("id", "a"), "num", 7);
      }
      Document c = doc("c");
      c.add(new BinaryDocValuesField("tag", new BytesRef("c-tag")));
      w.addDocument(c);
      if (earlyUpdate && earlyOnTag) {
        // "tag" must exist in the index before it can be updated: add c first, then update a
        w.updateBinaryDocValue(new Term("id", "a"), "tag", new BytesRef("early-tag"));
      }
      assertTrue(w.flushNextBuffer()); // _2 = {c}: fields id, num, tag

      AtomicReference<Throwable> lateErr = new AtomicReference<>();
      Thread b =
          new Thread(
              () -> {
                try {
                  switch (late) {
                    case BINARY_NEW_TO_SEGMENT ->
                        w.updateBinaryDocValue(new Term("id", "a"), "tag", new BytesRef("a-tag"));
                    case NUMERIC_EXISTING_FIELD ->
                        w.updateNumericDocValue(new Term("id", "a"), "num", 9);
                    case SOFT_DELETE ->
                        w.softUpdateDocument(
                            new Term("id", "a"), doc("a"), new NumericDocValuesField("del", 1));
                  }
                  w.addDocument(doc("d"));
                  w.flushNextBuffer(); // publishes packet P2 and applies it on this thread
                } catch (Throwable t) {
                  lateErr.set(t);
                }
              },
              "late-updater");
      if (pause) {
        is.victim = b;
      }
      b.start();
      if (pause) {
        assertTrue("late-updater never reached the window", is.reached.await(30, TimeUnit.SECONDS));
      } else {
        b.join();
      }
      try {
        if (nrtProbe) {
          // getReader(applyAllDeletes=false, writeAllDeletes=false) still writes all DV updates
          // (writeReaderPool(false) -> writeAllDocValuesUpdates) but does not wait for P2.
          try (DirectoryReader r = DirectoryReader.open(w, false, false)) {
            for (LeafReaderContext ctx : r.leaves()) {
              LeafReader leaf = ctx.reader();
              if (leaf.toString().contains("_0(")) {
                FieldInfo fi = leaf.getFieldInfos().fieldInfo("tag");
                out.fieldInfoOfTagInSeg0DuringWindow =
                    fi == null
                        ? "absent"
                        : (fi.getDocValuesType()
                            + " dvGen="
                            + fi.getDocValuesGen()
                            + " getBinaryDocValues="
                            + leaf.getBinaryDocValues("tag"));
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                PrintStream ps = new PrintStream(bos, true, StandardCharsets.UTF_8);
                CheckIndex.Status.DocValuesStatus st =
                    CheckIndex.testDocValues((CodecReader) leaf, ps, false);
                out.checkIndexDuringWindow =
                    st.error == null ? "OK" : String.valueOf(st.error);
              }
            }
          }
        }
        mp.names = List.of("_0", "_1");
        w.maybeMerge(); // SerialMergeScheduler: runs the merge of _0,_1 on this thread
      } catch (Throwable t) {
        out.mergeError = t;
      } finally {
        is.release.countDown();
        b.join();
      }
      out.lateError = lateErr.get();
      out.tragic = w.getTragicException();
      out.open = w.isOpen();
      out.trace = is.trace.toString();
      if (out.open && out.mergeError == null && out.lateError == null) {
        out.finalState = finalState(w);
      }
      return out;
    } finally {
      if (w.isOpen()) {
        w.close();
      } else {
        // the writer closed itself (tragic): what is left on disk is the last commit
        try (DirectoryReader r = DirectoryReader.open(dir)) {
          ByteArrayOutputStream bos = new ByteArrayOutputStream();
          CheckIndex.Status st;
          try (CheckIndex ci = new CheckIndex(dir)) {
            ci.setInfoStream(new PrintStream(bos, true, StandardCharsets.UTF_8));
            st = ci.checkIndex();
          }
          out.afterTragic =
              "lastCommit numDocs="
                  + r.numDocs()
                  + " segments="
                  + r.leaves().size()
                  + " CheckIndex.clean="
                  + st.clean;
        }
      }
      dir.close();
    }
  }

  /** "numDocs=N a.num=V a.tag=T" for the live doc(s) with id:a, from a fresh NRT reader. */
  private static String finalState(IndexWriter w) throws IOException {
    StringBuilder sb = new StringBuilder();
    try (DirectoryReader r = DirectoryReader.open(w)) {
      sb.append("numDocs=").append(r.numDocs()).append(" segs=").append(r.leaves().size());
      for (LeafReaderContext ctx : r.leaves()) {
        LeafReader leaf = ctx.reader();
        org.apache.lucene.util.Bits live = leaf.getLiveDocs();
        org.apache.lucene.index.StoredFields sf = leaf.storedFields();
        org.apache.lucene.index.NumericDocValues num = leaf.getNumericDocValues("num");
        BinaryDocValues tag = leaf.getBinaryDocValues("tag");
        for (int d = 0; d < leaf.maxDoc(); d++) {
          if (live != null && live.get(d) == false) {
            continue;
          }
          if ("a".equals(sf.document(d).get("id")) == false) {
            continue;
          }
          long nv = (num != null && num.advanceExact(d)) ? num.longValue() : Long.MIN_VALUE;
          String tv =
              (tag != null && tag.advanceExact(d)) ? tag.binaryValue().utf8ToString() : "<none>";
          sb.append(" a.num=").append(nv).append(" a.tag=").append(tv);
        }
      }
    }
    return sb.toString();
  }

  private static String describe(Outcome o) {
    StringBuilder sb = new StringBuilder();
    sb.append("mergeError=").append(o.mergeError);
    if (o.mergeError != null) {
      StackTraceElement[] st = o.mergeError.getStackTrace();
      for (int i = 0; i < Math.min(8, st.length); i++) {
        sb.append("\n    at ").append(st[i]);
      }
    }
    sb.append("\ntragic=").append(o.tragic).append(" isOpen=").append(o.open);
    sb.append("\nlateUpdaterError=").append(o.lateError);
    sb.append("\nfinalState=").append(o.finalState);
    if (o.afterTragic != null) {
      sb.append("\nafterTragic: ").append(o.afterTragic);
    }
    return sb.toString();
  }

  private static void assertHealthy(Outcome o) {
    if (o.mergeError != null || o.tragic != null || o.open == false || o.lateError != null) {
      AssertionError e = new AssertionError("expected merge to succeed:\n" + describe(o));
      if (o.mergeError != null) {
        e.initCause(o.mergeError);
      }
      throw e;
    }
  }

  /** BUG: binary DV update of a field new to _0, held between apply and finish, during merge. */
  public void testBinaryUpdateOfFieldNewToSegment() throws Exception {
    Outcome o = run(true, Late.BINARY_NEW_TO_SEGMENT, true, false);
    System.out.println("[binary-new-field] " + describe(o));
    assertHealthy(o);
    assertEquals("numDocs=4 segs=3 a.num=7 a.tag=a-tag", o.finalState);
  }

  /** BUG (soft-deletes flavour): the soft-deletes field is new to _0. */
  public void testSoftDeleteFieldNewToSegment() throws Exception {
    Outcome o = run(true, Late.SOFT_DELETE, true, false);
    System.out.println("[soft-delete] " + describe(o));
    assertHealthy(o);
    // old a (num=7) soft-deleted; live docs: new a (num=0), b, c, d
    assertEquals("numDocs=4 segs=3 a.num=0 a.tag=<none>", o.finalState);
  }

  /** Control: same window, but the late update targets a field _0 already has. */
  public void testControlLateUpdateOfExistingField() throws Exception {
    Outcome o = run(true, Late.NUMERIC_EXISTING_FIELD, true, false);
    System.out.println("[control existing field] " + describe(o));
    assertHealthy(o);
    assertEquals("numDocs=4 segs=3 a.num=9 a.tag=<none>", o.finalState);
  }

  /**
   * Control (scope): only ONE dv field is ever updated ("tag"). The early, finished update of tag
   * on a gives tag an applicable update in _0, so tag gets a real dvGen even though the late
   * update of tag is still unfinished. Expected to pass on main.
   */
  public void testControlSingleUpdatedFieldEarlyAndLate() throws Exception {
    Outcome o = run(true, true, Late.BINARY_NEW_TO_SEGMENT, true, false);
    System.out.println("[control single updated field] " + describe(o));
    assertHealthy(o);
    assertEquals("numDocs=4 segs=3 a.num=0 a.tag=a-tag", o.finalState);
  }

  /** Control: no finished-but-unwritten update on _0, so writeFieldUpdates has nothing to do. */
  public void testControlNoEarlyUpdate() throws Exception {
    Outcome o = run(false, Late.BINARY_NEW_TO_SEGMENT, true, false);
    System.out.println("[control no early update] " + describe(o));
    assertHealthy(o);
    assertEquals("numDocs=4 segs=3 a.num=0 a.tag=a-tag", o.finalState);
  }

  /** Control: the late packet is finished before the merge starts (no window). */
  public void testControlNoWindow() throws Exception {
    Outcome o = run(true, Late.BINARY_NEW_TO_SEGMENT, false, false);
    System.out.println("[control no window] " + describe(o));
    assertHealthy(o);
    assertEquals("numDocs=4 segs=3 a.num=7 a.tag=a-tag", o.finalState);
  }

  /**
   * BUG, observed without a merge first: an NRT reader opened inside the window (it writes the
   * DV updates but does not wait for P2) shows segment _0 with a BINARY field "tag" that has
   * dvGen=-1 and no values; CheckIndex on that leaf fails. Then the merge NPEs as well.
   */
  public void testNrtReaderInWindowSeesTypedFieldWithoutValues() throws Exception {
    Outcome o = run(true, Late.BINARY_NEW_TO_SEGMENT, true, true);
    System.out.println(
        "[nrt-probe] tag FieldInfo in _0 = "
            + o.fieldInfoOfTagInSeg0DuringWindow
            + "\n[nrt-probe] CheckIndex.testDocValues(_0) = "
            + o.checkIndexDuringWindow
            + "\n[nrt-probe] "
            + describe(o));
    assertEquals("OK", o.checkIndexDuringWindow);
    assertHealthy(o);
    assertEquals("numDocs=4 segs=3 a.num=7 a.tag=a-tag", o.finalState);
  }

  /**
   * After the control-no-window run, the update must be visible: sanity check that the scenario
   * is meaningful (a's tag becomes "a-tag" once everything settles).
   */
  public void testSanityUpdateVisibleWithoutRace() throws Exception {
    IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      w.addDocument(doc("a"));
      w.commit();
      Document c = doc("c");
      c.add(new BinaryDocValuesField("tag", new BytesRef("c-tag")));
      w.addDocument(c);
      w.updateBinaryDocValue(new Term("id", "a"), "tag", new BytesRef("a-tag"));
      w.forceMerge(1);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        IndexSearcher s = new IndexSearcher(r);
        TopDocs td = s.search(new TermQuery(new Term("id", "a")), 1);
        assertEquals(1, td.totalHits.value());
        int docId = td.scoreDocs[0].doc;
        LeafReaderContext ctx = r.leaves().get(0);
        BinaryDocValues bdv = ctx.reader().getBinaryDocValues("tag");
        assertTrue(bdv.advanceExact(docId - ctx.docBase));
        assertEquals(new BytesRef("a-tag"), bdv.binaryValue());
        assertEquals(DocValuesType.BINARY, ctx.reader().getFieldInfos().fieldInfo("tag").getDocValuesType());
      }
    }
  }

  // keep unused import checkers quiet
  @SuppressWarnings("unused")
  private static final Class<?> UNUSED = MergePolicy.class;
}
