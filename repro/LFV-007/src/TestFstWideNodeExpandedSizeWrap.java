import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import org.apache.lucene.store.ByteBuffersDataInput;
import org.apache.lucene.store.ByteBuffersDataOutput;
import org.apache.lucene.store.ByteBuffersIndexInput;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IntsRef;
import org.apache.lucene.util.fst.ByteSequenceOutputs;
import org.apache.lucene.util.fst.FST;
import org.apache.lucene.util.fst.FSTCompiler;
import org.apache.lucene.util.fst.IntsRefFSTEnum;
import org.apache.lucene.util.fst.OffHeapFSTStore;
import org.apache.lucene.util.fst.Util;

/**
 * Independent verification: a fixed-length-arc node whose expanded size
 * numArcs*maxBytesPerArc reaches 2^31 is compiled into a broken FST.
 *
 * <p>All keys are single-label sequences, so every arc hangs off the root node. All outputs are 1
 * byte except one "big" output on the arc with label 0. The arc bytes of that arc are
 * 1 (flags) + labelLen + vIntLen(bigLen) + bigLen; bigLen is chosen so that numArcs * that equals
 * exactly 2^31. Everything else about the FST is small (a few MB).
 */
public class TestFstWideNodeExpandedSizeWrap extends LuceneTestCase {

  static int vIntLen(int v) {
    int n = 1;
    while ((v & ~0x7F) != 0) {
      v >>>= 7;
      n++;
    }
    return n;
  }

  static BytesRef value(int i, int bigLen) {
    if (i == 0) {
      byte[] b = new byte[bigLen];
      for (int k = 0; k < bigLen; k++) b[k] = (byte) (k * 31 + 5);
      return new BytesRef(b);
    }
    return new BytesRef(new byte[] {(byte) (1 + (i % 100))});
  }

  static long counter(FSTCompiler<?> c, String name) throws Exception {
    Field f = FSTCompiler.class.getDeclaredField(name);
    f.setAccessible(true);
    return f.getLong(c);
  }

  /** Builds the FST; returns it, and prints which fixed-length encoding the root got. */
  static FST<BytesRef> build(FST.INPUT_TYPE type, int numArcs, int stride, int bigLen)
      throws Exception {
    FSTCompiler<BytesRef> c =
        new FSTCompiler.Builder<>(type, ByteSequenceOutputs.getSingleton()).build();
    for (int i = 0; i < numArcs; i++) {
      c.add(new IntsRef(new int[] {i * stride}, 0, 1), value(i, bigLen));
    }
    FST.FSTMetadata<BytesRef> md;
    try {
      md = c.compile();
    } finally {
      System.out.println(
          "  ["
              + type
              + " numArcs="
              + numArcs
              + " stride="
              + stride
              + " bigLen="
              + bigLen
              + "] BS="
              + counter(c, "binarySearchNodeCount")
              + " DA="
              + counter(c, "directAddressingNodeCount")
              + " CONT="
              + counter(c, "continuousNodeCount"));
    }
    FST<BytesRef> fst = FST.fromFSTReader(md, c.getFSTReader());
    System.out.println("  compile() returned normally; fst.numBytes=" + fst.numBytes());
    return fst;
  }

  /** Asserts every key maps to its value (via Util.get) and that enumeration sees every key. */
  static void checkAll(FST<BytesRef> fst, int numArcs, int stride, int bigLen) throws IOException {
    int wrong = 0, missing = 0, threw = 0;
    Throwable first = null;
    int checked = 0;
    for (int i = 0; i < numArcs; i++) {
      // For very wide nodes check a deterministic sample (a variable-length 1M-arc node, as produced
      // with allowFixedLengthArcs(false) or a size-guard fix, is a linear scan per lookup).
      if (numArcs > 65536 && i >= 2000 && i < numArcs - 2000 && i % 1009 != 0) continue;
      checked++;
      try {
        BytesRef got = Util.get(fst, new IntsRef(new int[] {i * stride}, 0, 1));
        if (got == null) missing++;
        else if (!got.equals(value(i, bigLen))) wrong++;
      } catch (RuntimeException e) {
        threw++;
        if (first == null) first = e;
      }
    }
    System.out.println(
        "  Util.get over " + checked + " of " + numArcs + " keys: wrong=" + wrong + " missing=" + missing + " threw="
            + threw + (first == null ? "" : " first=" + first));
    int enumerated = 0;
    Throwable enumErr = null;
    int enumWrong = 0;
    try {
      IntsRefFSTEnum<BytesRef> e = new IntsRefFSTEnum<>(fst);
      IntsRefFSTEnum.InputOutput<BytesRef> io;
      while ((io = e.next()) != null) {
        int expectedLabel = enumerated * stride;
        if (io.input.length != 1
            || io.input.ints[io.input.offset] != expectedLabel
            || !io.output.equals(value(enumerated, bigLen))) {
          enumWrong++;
          if (enumWrong <= 3) {
            System.out.println(
                "    enum#" + enumerated + " input=" + io.input + " outputLen=" + io.output.length);
          }
        }
        enumerated++;
        if (enumerated > numArcs + 10) break;
      }
    } catch (RuntimeException e) {
      enumErr = e;
    }
    System.out.println(
        "  IntsRefFSTEnum: enumerated="
            + enumerated
            + " wrong="
            + enumWrong
            + (enumErr == null ? "" : " threw=" + enumErr));
    if (first != null) throw new AssertionError("Util.get threw", first);
    if (enumErr != null) throw new AssertionError("enum threw", enumErr);
    assertEquals("wrong outputs", 0, wrong);
    assertEquals("missing keys", 0, missing);
    assertEquals("enum count", numArcs, enumerated);
    assertEquals("enum wrong", 0, enumWrong);
  }

  /** Save + reload off-heap to show the corruption persists in the serialized form. */
  static FST<BytesRef> saveReloadOffHeap(FST<BytesRef> fst) throws IOException {
    ByteBuffersDataOutput meta = new ByteBuffersDataOutput();
    ByteBuffersDataOutput data = new ByteBuffersDataOutput();
    fst.save(meta, data);
    ByteBuffersDataInput metaIn = meta.toDataInput();
    ByteBuffersDataInput dataIn = data.toDataInput();
    FST.FSTMetadata<BytesRef> md = FST.readMetadata(metaIn, ByteSequenceOutputs.getSingleton());
    return FST.fromFSTReader(
        md, new OffHeapFSTStore(new ByteBuffersIndexInput(dataIn, "fst"), 0, md));
  }

  // ---------------------------------------------------------------------------------------------

  /**
   * BYTE2: 2048 root arcs, labels 0,32,64,...,65504 (sparse -> binary search wins honestly: DA would
   * cost P + 2 - 2*2048 = 8191 + 2 - 4096 > 0 more bytes). Big arc at label 0 = 1 + 2 + 3 + bigLen
   * = 2^20 bytes, so numArcs * maxBytesPerArc = 2048 * 2^20 = 2^31.
   */
  public void testByte2SparseRoot_productExactly2pow31() throws Exception {
    int numArcs = 2048, stride = 32;
    int bigLen = (1 << 20) - 1 - 2 - 3;
    assertEquals(3, vIntLen(bigLen));
    FST<BytesRef> fst = build(FST.INPUT_TYPE.BYTE2, numArcs, stride, bigLen);
    checkAll(fst, numArcs, stride, bigLen);
  }

  /** Same FST, saved and reloaded off-heap: the corrupt node is persisted. */
  public void testByte2SparseRoot_savedAndReloaded() throws Exception {
    int numArcs = 2048, stride = 32;
    int bigLen = (1 << 20) - 1 - 2 - 3;
    FST<BytesRef> fst = saveReloadOffHeap(build(FST.INPUT_TYPE.BYTE2, numArcs, stride, bigLen));
    checkAll(fst, numArcs, stride, bigLen);
  }

  /**
   * BYTE4, 1,048,576 root arcs, labels 0,16,32,... (sparse -> BS). Only one value needs to be 2044
   * bytes: arc = 1 + 1 (vInt label 0) + 2 (vInt len) + 2044 = 2048, and 2^20 * 2^11 = 2^31.
   */
  public void testByte4MillionArcRoot_one2KValue() throws Exception {
    int numArcs = 1 << 20, stride = 16;
    int bigLen = 2048 - 1 - 1 - 2;
    assertEquals(2, vIntLen(bigLen));
    FST<BytesRef> fst = build(FST.INPUT_TYPE.BYTE4, numArcs, stride, bigLen);
    checkAll(fst, numArcs, stride, bigLen);
  }

  /** Control: same 1M-arc BYTE4 shape, big value of 196 bytes (expanded 2^20*200 bytes = 200 MB, no wrap). */
  public void testByte4MillionArcRoot_control200() throws Exception {
    int numArcs = 1 << 20, stride = 16;
    int bigLen = 200 - 1 - 1 - 2;
    FST<BytesRef> fst = build(FST.INPUT_TYPE.BYTE4, numArcs, stride, bigLen);
    checkAll(fst, numArcs, stride, bigLen);
  }

  /** Control: BYTE2 2048-arc shape with a 64 KB value (expanded ~128 MB, no wrap). */
  public void testByte2SparseRoot_control64K() throws Exception {
    int numArcs = 2048, stride = 32;
    int bigLen = 65536;
    FST<BytesRef> fst = build(FST.INPUT_TYPE.BYTE2, numArcs, stride, bigLen);
    checkAll(fst, numArcs, stride, bigLen);
  }

  /**
   * Continuous labels (0..2047) -> ARCS_FOR_CONTINUOUS path (writeNodeForDirectAddressingOrContinuous,
   * totalArcBytes wraps). Characterises the other branch: is it loud or silent?
   */
  public void testByte2ContinuousRoot_productWraps() throws Exception {
    int numArcs = 2048, stride = 1;
    int bigLen = (1 << 20) - 1 - 2 - 3 + 8; // maxBytesPerArcWithoutLabel*2048 > 2^31 too
    FST<BytesRef> fst = build(FST.INPUT_TYPE.BYTE2, numArcs, stride, bigLen);
    checkAll(fst, numArcs, stride, bigLen);
  }

  /** Workaround check: allowFixedLengthArcs(false) on the failing BYTE2 shape works. */
  public void testByte2SparseRoot_fixedLengthArcsDisabledWorks() throws Exception {
    int numArcs = 2048, stride = 32;
    int bigLen = (1 << 20) - 1 - 2 - 3;
    FSTCompiler<BytesRef> c =
        new FSTCompiler.Builder<>(FST.INPUT_TYPE.BYTE2, ByteSequenceOutputs.getSingleton())
            .allowFixedLengthArcs(false)
            .build();
    for (int i = 0; i < numArcs; i++) {
      c.add(new IntsRef(new int[] {i * stride}, 0, 1), value(i, bigLen));
    }
    FST<BytesRef> fst = FST.fromFSTReader(c.compile(), c.getFSTReader());
    checkAll(fst, numArcs, stride, bigLen);
  }

  /**
   * Silent wrong answers without any exception (-da): the first entry returned by an enumerator
   * over the corrupt FST is a key that was never added. Also probes seekCeil/seekFloor/seekExact.
   */
  public void testByte2SparseRoot_firstEntryIsBogusWithoutException() throws Exception {
    int numArcs = 2048, stride = 32;
    int bigLen = (1 << 20) - 1 - 2 - 3;
    // saved + reloaded off-heap (the on-heap reader happens to throw on the very first read here)
    FST<BytesRef> fst = saveReloadOffHeap(build(FST.INPUT_TYPE.BYTE2, numArcs, stride, bigLen));
    StringBuilder report = new StringBuilder();
    probe(report, "next()", () -> new IntsRefFSTEnum<>(fst).next());
    probe(report, "seekCeil([0])", () -> new IntsRefFSTEnum<>(fst).seekCeil(ints(0)));
    probe(report, "seekCeil([1])", () -> new IntsRefFSTEnum<>(fst).seekCeil(ints(1)));
    probe(report, "seekFloor([65535])", () -> new IntsRefFSTEnum<>(fst).seekFloor(ints(65535)));
    probe(report, "seekExact([0])", () -> new IntsRefFSTEnum<>(fst).seekExact(ints(0)));
    probe(report, "seekExact([32])", () -> new IntsRefFSTEnum<>(fst).seekExact(ints(32)));
    probe(
        report,
        "firstRealTargetArc",
        () -> {
          FST.Arc<BytesRef> arc = fst.getFirstArc(new FST.Arc<>());
          FST.BytesReader r = fst.getBytesReader();
          FST.Arc<BytesRef> a = fst.readFirstRealTargetArc(arc.target(), new FST.Arc<>(), r);
          return "label=" + a.label() + " outputLen=" + a.output().length;
        });
    System.out.print(report);
    // The correct first entry is key [0] with the big value.
    IntsRefFSTEnum.InputOutput<BytesRef> io = new IntsRefFSTEnum<>(fst).next();
    assertNotNull(io);
    assertEquals("first key", 0, io.input.ints[io.input.offset]);
    assertEquals("first value", value(0, bigLen), io.output);
  }

  interface Op {
    Object run() throws Exception;
  }

  static IntsRef ints(int v) {
    return new IntsRef(new int[] {v}, 0, 1);
  }

  static void probe(StringBuilder sb, String name, Op op) {
    try {
      Object r = op.run();
      String desc;
      if (r instanceof IntsRefFSTEnum.InputOutput<?> io) {
        desc =
            "returned input="
                + io.input
                + " outputLen="
                + (io.output == null ? "null" : ((BytesRef) io.output).length);
      } else {
        desc = "returned " + r;
      }
      sb.append("    ").append(name).append(" -> ").append(desc).append('\n');
    } catch (Throwable t) {
      sb.append("    ").append(name).append(" -> threw ").append(t).append('\n');
    }
  }

  /**
   * Latent corruption below the root: 100 normal keys [k] plus a 2048-way sparse BYTE2 node under
   * prefix [7000]. Keys that do not pass through the bad node still work, so the FST looks healthy
   * until a key under [7000, *] is looked up.
   */
  public void testByte2DeepNode_onlyKeysUnderBadNodeBreak() throws Exception {
    int numArcs = 2048, stride = 32;
    int bigLen = (1 << 20) - 1 - 2 - 3;
    FSTCompiler<BytesRef> c =
        new FSTCompiler.Builder<>(FST.INPUT_TYPE.BYTE2, ByteSequenceOutputs.getSingleton()).build();
    for (int k = 0; k < 100; k++) {
      c.add(ints(k * 7), new BytesRef(new byte[] {(byte) k}));
    }
    for (int i = 0; i < numArcs; i++) {
      c.add(new IntsRef(new int[] {7000, i * stride}, 0, 2), value(i, bigLen));
    }
    FST<BytesRef> fst = FST.fromFSTReader(c.compile(), c.getFSTReader());
    System.out.println(
        "  deep: compile() returned; BS=" + counter(c, "binarySearchNodeCount")
            + " DA=" + counter(c, "directAddressingNodeCount"));
    int okTop = 0;
    for (int k = 0; k < 100; k++) {
      if (new BytesRef(new byte[] {(byte) k}).equals(Util.get(fst, ints(k * 7)))) okTop++;
    }
    System.out.println("  deep: top-level keys correct: " + okTop + "/100");
    assertEquals(100, okTop);
    int ok = 0, threw = 0, wrongOrMissing = 0;
    Throwable first = null;
    for (int i = 0; i < numArcs; i++) {
      try {
        BytesRef got = Util.get(fst, new IntsRef(new int[] {7000, i * stride}, 0, 2));
        if (value(i, bigLen).equals(got)) ok++;
        else wrongOrMissing++;
      } catch (RuntimeException e) {
        threw++;
        if (first == null) first = e;
      }
    }
    System.out.println(
        "  deep: keys under bad node: ok=" + ok + " wrongOrMissing=" + wrongOrMissing + " threw="
            + threw + (first == null ? "" : " first=" + first));
    assertEquals(numArcs, ok);
  }
}
