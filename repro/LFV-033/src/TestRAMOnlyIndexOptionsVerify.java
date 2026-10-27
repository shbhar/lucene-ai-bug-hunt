import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

/**
 * Independent verification of the finding: RAMOnlyPostingsFormat (lucene-test-framework) and
 * fields indexed without positions. Deterministic: fixed docs, ByteBuffersDirectory, explicit
 * codecs, NoMergePolicy, explicit flush(); no LuceneTestCase randomization is consulted.
 */
public class TestRAMOnlyIndexOptionsVerify extends LuceneTestCase {

  private static final String[] DOCS = {"a a b", "b c", "a c c c", "d", "a b a b"};

  private static Codec ramOnly() {
    return TestUtil.alwaysPostingsFormat(PostingsFormat.forName("RAMOnly"));
  }

  private static Codec patchedRamOnly() {
    return TestUtil.alwaysPostingsFormat(PostingsFormat.forName("RAMOnlyPatched"));
  }

  private static FieldType type(IndexOptions opts) {
    FieldType ft = new FieldType();
    ft.setTokenized(true);
    ft.setIndexOptions(opts);
    ft.freeze();
    return ft;
  }

  /**
   * Compound files are OFF by default here: RAMOnly's ".id" file has a plain CodecUtil.writeHeader
   * header and no footer, which Lucene90CompoundFormat rejects (separate issue, see
   * testAnyFieldFailsWithCompoundFile).
   */
  private static IndexWriterConfig iwc(Codec codec) {
    return iwc(codec, false);
  }

  private static IndexWriterConfig iwc(Codec codec, boolean cfs) {
    return new IndexWriterConfig(new StandardAnalyzer())
        .setCodec(codec)
        .setMergePolicy(NoMergePolicy.INSTANCE)
        .setUseCompoundFile(cfs)
        .setCommitOnClose(false);
  }

  private static String top(Throwable t) {
    StackTraceElement e = t.getStackTrace()[0];
    return e.getClassName() + "." + e.getMethodName() + "(" + e.getFileName() + ":" + e.getLineNumber() + ")";
  }

  private static String trace(Throwable t, int frames) {
    StringBuilder sb = new StringBuilder(t.toString());
    StackTraceElement[] st = t.getStackTrace();
    for (int i = 0; i < Math.min(frames, st.length); i++) {
      sb.append("\n      at ").append(st[i]);
    }
    return sb.toString();
  }

  /** Result of indexing DOCS with one field of the given options and flushing. */
  private static final class Outcome {
    boolean addDocumentsOk;
    Throwable flushFailure;
    boolean writerOpenAfter;
    Throwable tragic;
  }

  private static Outcome indexAndFlush(Codec codec, FieldType ft) throws IOException {
    return indexAndFlush(codec, ft, false);
  }

  private static Outcome indexAndFlush(Codec codec, FieldType ft, boolean cfs) throws IOException {
    Outcome o = new Outcome();
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w = new IndexWriter(dir, iwc(codec, cfs));
      try {
        for (String s : DOCS) {
          Document d = new Document();
          d.add(new Field("f", s, ft));
          w.addDocument(d);
        }
        o.addDocumentsOk = true;
        try {
          w.flush();
        } catch (Throwable t) {
          o.flushFailure = t;
        }
        o.writerOpenAfter = w.isOpen();
        o.tragic = w.getTragicException();
      } finally {
        if (w.isOpen()) {
          w.rollback();
        }
      }
    }
    return o;
  }

  // ---------------------------------------------------------------- the candidate claim

  /** DOCS-only tokenized field + RAMOnly: flush throws NegativeArraySizeException: -1. */
  public void testDocsOnlyFieldFailsAtFlush() throws Exception {
    for (boolean cfs : new boolean[] {false, true}) {
      checkDocsOnlyFailure(cfs);
    }
  }

  private void checkDocsOnlyFailure(boolean cfs) throws Exception {
    Outcome o = indexAndFlush(ramOnly(), type(IndexOptions.DOCS), cfs);
    System.out.println("[DOCS + RAMOnly, useCompoundFile=" + cfs + "]");
    assertTrue("addDocument must succeed (failure is at flush)", o.addDocumentsOk);
    assertNotNull("flush should have failed", o.flushFailure);
    System.out.println("[DOCS + RAMOnly] flush threw: " + trace(o.flushFailure, 4));
    System.out.println("[DOCS + RAMOnly] writer.isOpen()=" + o.writerOpenAfter
        + " tragic=" + o.tragic);
    assertTrue(o.flushFailure instanceof NegativeArraySizeException);
    assertEquals("-1", o.flushFailure.getMessage());
    StackTraceElement e = o.flushFailure.getStackTrace()[0];
    assertEquals(
        "org.apache.lucene.tests.codecs.ramonly.RAMOnlyPostingsFormat$RAMDoc", e.getClassName());
    assertEquals("<init>", e.getMethodName());
    // A flush-time exception in the indexing chain is an aborting exception -> tragic event.
    assertFalse(o.writerOpenAfter);
    assertSame(o.flushFailure, o.tragic);
  }

  /** The exact trigger from the candidate: a StringField (DOCS, not tokenized). */
  public void testStringFieldFailsAtFlush() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      // default IndexWriterConfig settings, like the candidate's repro
      IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()).setCodec(ramOnly()));
      Document d = new Document();
      d.add(new StringField("id", "x", Field.Store.NO));
      w.addDocument(d);
      NegativeArraySizeException e = expectThrows(NegativeArraySizeException.class, w::flush);
      System.out.println("[StringField + RAMOnly] flush threw: " + trace(e, 2));
      assertFalse(w.isOpen());
    }
  }

  // ---------------------------------------------------------------- controls

  /** Control: same DOCS-only field with the default codec works. */
  public void testDocsOnlyDefaultCodecWorks() throws Exception {
    Outcome o = indexAndFlush(TestUtil.getDefaultCodec(), type(IndexOptions.DOCS));
    assertNull(o.flushFailure);
    assertTrue(o.writerOpenAfter);
    System.out.println("[DOCS + default codec] flush OK");
  }

  /** Control: positions-indexed field (no offsets) is the one shape RAMOnly fully supports. */
  public void testPositionsFieldWorks() throws Exception {
    Outcome o = indexAndFlush(ramOnly(), type(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS));
    assertNull(o.flushFailure);
    System.out.println("[DOCS_AND_FREQS_AND_POSITIONS + RAMOnly] flush OK");
  }

  /** Control: offsets are rejected on purpose, with an explicit message. */
  public void testOffsetsExplicitlyRejected() throws Exception {
    Outcome o =
        indexAndFlush(ramOnly(), type(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS));
    assertNotNull(o.flushFailure);
    System.out.println("[OFFSETS + RAMOnly] flush threw: " + o.flushFailure);
    assertTrue(o.flushFailure instanceof UnsupportedOperationException);
    assertEquals("this codec cannot index offsets", o.flushFailure.getMessage());
  }

  /**
   * Related shape (beyond the candidate): DOCS_AND_FREQS. With -ea (which LuceneTestCase requires,
   * TestRuleAssertionsRequired) finishDoc's "assert posUpto == current.positions.length" trips
   * because the positions array is sized by freq but no positions are ever added.
   */
  public void testDocsAndFreqsFieldTripsAssertion() throws Exception {
    Outcome o = indexAndFlush(ramOnly(), type(IndexOptions.DOCS_AND_FREQS));
    assertNotNull(o.flushFailure);
    System.out.println("[DOCS_AND_FREQS + RAMOnly] flush threw: " + trace(o.flushFailure, 3));
    assertTrue(o.flushFailure instanceof AssertionError);
    assertEquals(
        "org.apache.lucene.tests.codecs.ramonly.RAMOnlyPostingsFormat$RAMPostingsWriterImpl",
        o.flushFailure.getStackTrace()[0].getClassName());
    assertEquals("finishDoc", o.flushFailure.getStackTrace()[0].getMethodName());
    assertFalse(o.writerOpenAfter);
  }

  /**
   * Separate, broader incompatibility found while building the controls: with compound files on
   * (IndexWriterConfig default), RAMOnly fails at flush for ANY field, because its 17-byte ".id"
   * file lacks an index header + footer that Lucene90CompoundFormat verifies (LUCENE-7323).
   */
  public void testAnyFieldFailsWithCompoundFile() throws Exception {
    Outcome o = indexAndFlush(ramOnly(), type(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS), true);
    assertNotNull(o.flushFailure);
    System.out.println("[POSITIONS + RAMOnly, useCompoundFile=true] flush threw: "
        + trace(o.flushFailure, 3));
    assertTrue(o.flushFailure instanceof org.apache.lucene.index.CorruptIndexException);
    assertTrue(o.flushFailure.getMessage(), o.flushFailure.getMessage().contains(
        "compound sub-files must have a valid codec header and footer"));
    assertFalse(o.writerOpenAfter);
  }

  /**
   * Separate defect found while building the comparison: RAMDocsEnum.docID() dereferences a null
   * "current" before nextDoc() (DocIdSetIterator#docID requires -1), so any conjunction over a
   * RAMOnly field (PhraseQuery, BooleanQuery MUST+MUST) throws NullPointerException.
   */
  public void testUnpositionedDocIdNpe() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, iwc(ramOnly()))) {
        for (String s : DOCS) {
          Document d = new Document();
          d.add(new Field("f", s, type(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS)));
          w.addDocument(d);
        }
        w.flush();
        try (DirectoryReader r = DirectoryReader.open(w)) {
          IndexSearcher s = new IndexSearcher(r);
          s.setQueryCache(null);
          assertEquals(3, s.count(new TermQuery(new Term("f", "a"))));
          PostingsEnum pe =
              r.leaves().get(0).reader().postings(new Term("f", "a"), PostingsEnum.FREQS);
          NullPointerException e1 = expectThrows(NullPointerException.class, pe::docID);
          System.out.println("[RAMOnly] unpositioned PostingsEnum.docID(): " + e1.getMessage());
          // Separate defect: positions are stored as -1 (enumFlags=0 == PostingsEnum.NONE).
          PostingsEnum pp =
              r.leaves().get(0).reader().postings(new Term("f", "a"), PostingsEnum.POSITIONS);
          assertEquals(0, pp.nextDoc());
          assertEquals(2, pp.freq());
          int p0 = pp.nextPosition();
          int p1 = pp.nextPosition();
          System.out.println("[RAMOnly] doc0 \"a a b\" term a positions: " + p0 + "," + p1
              + " (default codec: 0,1)");
          assertEquals(-1, p0);
          assertEquals(-1, p1);
          NullPointerException e2 =
              expectThrows(
                  NullPointerException.class, () -> s.count(new PhraseQuery("f", "a", "b")));
          System.out.println("[RAMOnly] PhraseQuery: " + trace(e2, 2));
          org.apache.lucene.search.BooleanQuery.Builder bq =
              new org.apache.lucene.search.BooleanQuery.Builder();
          bq.add(new TermQuery(new Term("f", "a")), org.apache.lucene.search.BooleanClause.Occur.MUST);
          bq.add(new TermQuery(new Term("f", "b")), org.apache.lucene.search.BooleanClause.Occur.MUST);
          Throwable e3 = expectThrows(Throwable.class, () -> s.count(bq.build()));
          System.out.println("[RAMOnly] BooleanQuery a AND b: " + trace(e3, 2));
        }
      }
    }
  }

  // ---------------------------------------------------------------- root-cause confirmation

  private static List<String> dump(Codec codec, IndexOptions opts) throws IOException {
    List<String> out = new ArrayList<>();
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, iwc(codec))) {
        for (String s : DOCS) {
          Document d = new Document();
          d.add(new Field("f", s, type(opts)));
          w.addDocument(d);
        }
        w.flush();
        try (DirectoryReader r = DirectoryReader.open(w)) {
          assertEquals(1, r.leaves().size());
          LeafReader leaf = r.leaves().get(0).reader();
          Terms terms = leaf.terms("f");
          out.add("docCount=" + terms.getDocCount() + " sumDocFreq=" + terms.getSumDocFreq()
              + " sumTTF=" + terms.getSumTotalTermFreq() + " hasFreqs=" + terms.hasFreqs()
              + " hasPositions=" + terms.hasPositions());
          TermsEnum te = terms.iterator();
          for (BytesRef t = te.next(); t != null; t = te.next()) {
            StringBuilder sb = new StringBuilder(t.utf8ToString());
            sb.append(" df=").append(te.docFreq()).append(" ttf=").append(te.totalTermFreq());
            int flags = opts.compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) >= 0
                ? PostingsEnum.POSITIONS : PostingsEnum.FREQS;
            PostingsEnum pe = te.postings(null, flags);
            for (int doc = pe.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = pe.nextDoc()) {
              sb.append(" [").append(doc).append(" freq=").append(pe.freq());
              if (flags == PostingsEnum.POSITIONS) {
                sb.append(" pos=");
                for (int i = 0; i < pe.freq(); i++) {
                  sb.append(pe.nextPosition()).append(',');
                }
              }
              sb.append(']');
            }
            out.add(sb.toString());
          }
          IndexSearcher s = new IndexSearcher(r);
          out.add("count(a)=" + s.count(new TermQuery(new Term("f", "a"))));
          if (hasPositions(opts)) {
            out.add("count(\"a b\")=" + s.count(new PhraseQuery("f", "a", "b")));
          }
        }
      }
    }
    return out;
  }

  private static boolean hasPositions(IndexOptions opts) {
    return opts.compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) >= 0;
  }

  /**
   * With the experimental patch (freq kept separately from the positions array; freq=1 for DOCS),
   * every non-offset IndexOptions indexes, and terms/postings/queries match the default codec.
   */
  public void testPatchedCopyMatchesDefaultCodec() throws Exception {
    for (IndexOptions opts :
        new IndexOptions[] {
          IndexOptions.DOCS, IndexOptions.DOCS_AND_FREQS, IndexOptions.DOCS_AND_FREQS_AND_POSITIONS
        }) {
      List<String> expected = dump(TestUtil.getDefaultCodec(), opts);
      List<String> actual = dump(patchedRamOnly(), opts);
      System.out.println("[patched RAMOnly vs default, " + opts + "] " + actual);
      assertEquals(opts.toString(), expected, actual);
    }
  }
}
