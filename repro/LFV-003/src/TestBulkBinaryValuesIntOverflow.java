import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.document.BinaryDocValuesField;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.CheckIndex;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FilterBinaryDocValues;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

/**
 * Independent repro: Lucene90 bulk BinaryDocValues#binaryValues sizes its single buffer with int
 * arithmetic. Each test builds a small (2 or 3 doc) real index on disk with ~1 GiB binary values
 * (each value well below the documented per-value limit BinaryDocValuesWriter.MAX_LENGTH =
 * ArrayUtil.MAX_ARRAY_LENGTH), then compares the specialised bulk call against controls that the
 * javadoc says it must behave like.
 *
 * <p>Run each test method in its own JVM (-Dtests.method=...) to keep the heap below 8 GiB.
 */
@LuceneTestCase.SuppressSysoutChecks(bugUrl = "verification output")
public class TestBulkBinaryValuesIntOverflow extends LuceneTestCase {

  static final int GIB = 1 << 30;

  /** Value of doc d, length len: byte k = (byte)(k * 31 + d * 7 + 1). Only a few bytes are probed. */
  static byte expectedByte(int doc, long k) {
    return (byte) (k * 31 + doc * 7 + 1);
  }

  static void fill(byte[] b, int len, int doc) {
    for (int k = 0; k < len; k++) {
      b[k] = expectedByte(doc, k);
    }
  }

  /** Probe first, middle and last byte of a returned value. */
  static void checkValue(String what, BytesRef v, int doc, int len) {
    assertNotNull(what + " doc=" + doc + " null", v);
    assertEquals(what + " doc=" + doc + " length", len, v.length);
    assertTrue(what + " BytesRef invalid", v.isValid());
    for (long k : new long[] {0, len / 2, len - 1}) {
      assertEquals(
          what + " doc=" + doc + " byte " + k, expectedByte(doc, k), v.bytes[v.offset + (int) k]);
    }
  }

  /** Builds a single-segment index whose field "f" has values of the given lengths for docs 0..n-1. */
  Directory buildIndex(Path path, int[] lengths) throws Exception {
    Directory dir = FSDirectory.open(path);
    Codec codec = TestUtil.getDefaultCodec(false); // Lucene104Codec -> Lucene90DocValuesFormat
    IndexWriterConfig iwc =
        new IndexWriterConfig(null)
            .setCodec(codec)
            .setUseCompoundFile(false)
            .setMaxBufferedDocs(100)
            .setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH)
            .setRAMPerThreadHardLimitMB(2047)
            .setMergePolicy(NoMergePolicy.INSTANCE);
    int maxLen = 0;
    for (int l : lengths) maxLen = Math.max(maxLen, l);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      byte[] buf = new byte[maxLen];
      for (int d = 0; d < lengths.length; d++) {
        fill(buf, lengths[d], d);
        Document doc = new Document();
        doc.add(new BinaryDocValuesField("f", new BytesRef(buf, 0, lengths[d])));
        w.addDocument(doc);
      }
      buf = null;
      w.commit();
    }
    // The per-DWPT hard limit (2047 MB) may have split the docs into several segments: merge.
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      if (r.leaves().size() > 1) {
        IndexWriterConfig iwc2 =
            new IndexWriterConfig(null).setCodec(codec).setUseCompoundFile(false);
        try (IndexWriter w = new IndexWriter(dir, iwc2)) {
          w.forceMerge(1);
        }
      }
    }
    return dir;
  }

  static String top(Throwable t) {
    StringBuilder sb = new StringBuilder(t.toString());
    StackTraceElement[] st = t.getStackTrace();
    for (int i = 0; i < Math.min(2, st.length); i++) sb.append("\n      at ").append(st[i]);
    return sb.toString();
  }

  /** Fixed length (all values 2^30 bytes), contiguous docs {0,1}: size*length = 2^31. */
  public void testFixedLengthContiguous() throws Exception {
    int len = GIB;
    try (Directory dir = buildIndex(createTempDir("fixed2"), new int[] {len, len});
        DirectoryReader r = DirectoryReader.open(dir)) {
      assertEquals(1, r.leaves().size());
      LeafReader leaf = r.leaves().get(0).reader();
      assertEquals(2, leaf.maxDoc());
      System.out.println("codec=" + Codec.getDefault().getName() + " (index written with Lucene104)");
      System.out.println("leaf dv producer class=" + leaf.getBinaryDocValues("f").getClass().getName());

      // Control 1: per-value API on both docs.
      BinaryDocValues dv = leaf.getBinaryDocValues("f");
      for (int d = 0; d < 2; d++) {
        assertTrue(dv.advanceExact(d));
        checkValue("binaryValue()", dv.binaryValue(), d, len);
      }
      dv = null;
      System.out.println("CONTROL per-value binaryValue() on docs 0,1: OK");

      // Control 2: the specialised bulk call on one doc at a time (size*length = 2^30) works.
      for (int d = 0; d < 2; d++) {
        BytesRef[] one = new BytesRef[1];
        leaf.getBinaryDocValues("f").binaryValues(1, new int[] {d}, one);
        checkValue("bulk size=1", one[0], d, len);
      }
      System.out.println("CONTROL bulk binaryValues(size=1) on doc 0 and on doc 1: OK");

      // Control 3: javadoc reference implementation (default method, via a filter that does not
      // override binaryValues) on the same two docs.
      {
        BytesRef[] vals = new BytesRef[2];
        new FilterBinaryDocValues(leaf.getBinaryDocValues("f")) {}.binaryValues(
            2, new int[] {0, 1}, vals);
        checkValue("default binaryValues", vals[0], 0, len);
        checkValue("default binaryValues", vals[1], 1, len);
        System.out.println("CONTROL default BinaryDocValues#binaryValues(2,{0,1}): OK");
      }

      // Bug: specialised Lucene90 bulk call over the same two docs.
      BytesRef[] vals = new BytesRef[2];
      Throwable t =
          expectThrows(
              NegativeArraySizeException.class,
              () -> leaf.getBinaryDocValues("f").binaryValues(2, new int[] {0, 1}, vals));
      System.out.println("BUG fixed/contiguous binaryValues(2,{0,1}) -> " + top(t));
      assertEquals("-2147483648", t.getMessage());
    }
  }

  /** Variable length (2^30 and 2^30+1), contiguous docs {0,1}: lastEnd-firstStart = 2^31+1. */
  public void testVariableLengthContiguous() throws Exception {
    int[] lens = {GIB, GIB + 1};
    try (Directory dir = buildIndex(createTempDir("var2"), lens);
        DirectoryReader r = DirectoryReader.open(dir)) {
      assertEquals(1, r.leaves().size());
      LeafReader leaf = r.leaves().get(0).reader();
      assertEquals(2, leaf.maxDoc());

      BinaryDocValues dv = leaf.getBinaryDocValues("f");
      for (int d = 0; d < 2; d++) {
        assertTrue(dv.advanceExact(d));
        checkValue("binaryValue()", dv.binaryValue(), d, lens[d]);
      }
      dv = null;
      System.out.println("CONTROL per-value binaryValue() on docs 0,1: OK");

      for (int d = 0; d < 2; d++) {
        BytesRef[] one = new BytesRef[1];
        leaf.getBinaryDocValues("f").binaryValues(1, new int[] {d}, one);
        checkValue("bulk size=1", one[0], d, lens[d]);
      }
      System.out.println("CONTROL bulk binaryValues(size=1) on doc 0 and on doc 1: OK");

      {
        BytesRef[] vals = new BytesRef[2];
        new FilterBinaryDocValues(leaf.getBinaryDocValues("f")) {}.binaryValues(
            2, new int[] {0, 1}, vals);
        checkValue("default binaryValues", vals[0], 0, lens[0]);
        checkValue("default binaryValues", vals[1], 1, lens[1]);
        System.out.println("CONTROL default BinaryDocValues#binaryValues(2,{0,1}): OK");
      }

      BytesRef[] vals = new BytesRef[2];
      Throwable t =
          expectThrows(
              NegativeArraySizeException.class,
              () -> leaf.getBinaryDocValues("f").binaryValues(2, new int[] {0, 1}, vals));
      System.out.println("BUG variable/contiguous binaryValues(2,{0,1}) -> " + top(t));
      assertEquals("-2147483647", t.getMessage());
    }
  }

  /**
   * Consequence check: CheckIndex level 2 + exorciseIndex drops the (valid) segment, i.e. all 3
   * documents. Control: the same docs are fully readable before exorcising.
   */
  public void testCheckIndexExorciseDropsValidSegment() throws Exception {
    int len = GIB;
    try (Directory dir = buildIndex(createTempDir("exorcise"), new int[] {len, len, len})) {
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        System.out.println("before: numDocs=" + r.numDocs() + " leaves=" + r.leaves().size());
        assertEquals(3, r.numDocs());
      }
      CheckIndex.Status st;
      try (CheckIndex ci = new CheckIndex(dir)) {
        ci.setLevel(CheckIndex.Level.MIN_LEVEL_FOR_INTEGRITY_CHECKS);
        st = ci.checkIndex();
        System.out.println(
            "CheckIndex level=2 clean=" + st.clean + " totLoseDocCount=" + st.totLoseDocCount);
        assertFalse(st.clean);
        ci.exorciseIndex(st);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        System.out.println("after exorciseIndex: numDocs=" + r.numDocs() + " leaves=" + r.leaves().size());
        assertEquals("BUG: valid docs removed by exorcise", 0, r.numDocs());
      }
    }
  }

  /**
   * Fixed length 2^30, 3 docs, NON-contiguous docs {0,2}: the fixed-length path allocates
   * size*length before testing contiguity. CheckIndex (level >= 2) calls the bulk API with
   * non-contiguous batches {0,2,5,9,...} and therefore reports this valid index as corrupt.
   */
  public void testFixedLengthNonContiguousAndCheckIndex() throws Exception {
    int len = GIB;
    try (Directory dir = buildIndex(createTempDir("fixed3"), new int[] {len, len, len})) {
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(1, r.leaves().size());
        LeafReader leaf = r.leaves().get(0).reader();
        assertEquals(3, leaf.maxDoc());

        BinaryDocValues dv = leaf.getBinaryDocValues("f");
        for (int d = 0; d < 3; d++) {
          assertTrue(dv.advanceExact(d));
          checkValue("binaryValue()", dv.binaryValue(), d, len);
        }
        dv = null;
        System.out.println("CONTROL per-value binaryValue() on docs 0,1,2: OK");

        {
          BytesRef[] vals = new BytesRef[2];
          new FilterBinaryDocValues(leaf.getBinaryDocValues("f")) {}.binaryValues(
              2, new int[] {0, 2}, vals);
          checkValue("default binaryValues", vals[0], 0, len);
          checkValue("default binaryValues", vals[1], 2, len);
          System.out.println("CONTROL default BinaryDocValues#binaryValues(2,{0,2}): OK");
        }

        BytesRef[] vals = new BytesRef[2];
        Throwable t =
            expectThrows(
                NegativeArraySizeException.class,
                () -> leaf.getBinaryDocValues("f").binaryValues(2, new int[] {0, 2}, vals));
        System.out.println("BUG fixed/NON-contiguous binaryValues(2,{0,2}) -> " + top(t));
      }

      // CheckIndex control: level 1 (checksums only, the CLI default) is clean.
      for (int level : new int[] {CheckIndex.Level.MIN_LEVEL_FOR_CHECKSUM_CHECKS,
                                  CheckIndex.Level.MIN_LEVEL_FOR_INTEGRITY_CHECKS}) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        CheckIndex.Status st;
        try (CheckIndex ci = new CheckIndex(dir)) {
          ci.setLevel(level);
          ci.setInfoStream(new PrintStream(bos, true, StandardCharsets.UTF_8));
          st = ci.checkIndex();
        }
        String out = bos.toString(StandardCharsets.UTF_8);
        List<String> interesting = new ArrayList<>();
        for (String line : out.split("\n")) {
          if (line.contains("docvalues") || line.contains("FAILED") || line.contains("WARNING")
              || line.contains("NegativeArraySize") || line.contains("Lucene90DocValuesProducer")
              || line.contains("checkBulkFetchBinaryDocValues") || line.startsWith("No problems")
              || line.contains("broken segments")) {
            interesting.add(line);
          }
        }
        System.out.println("CheckIndex level=" + level + " clean=" + st.clean
            + " numBadSegments=" + st.numBadSegments + " totLoseDocCount=" + st.totLoseDocCount);
        for (String l : interesting) System.out.println("   | " + l);
        if (level == CheckIndex.Level.MIN_LEVEL_FOR_CHECKSUM_CHECKS) {
          assertTrue("level 1 should be clean", st.clean);
        } else {
          assertFalse("BUG: level 2 flags a valid index as corrupt", st.clean);
          Throwable e = st.segmentInfos.get(0).docValuesStatus.error;
          assertTrue(String.valueOf(e), e instanceof NegativeArraySizeException);
        }
      }
    }
  }
}
