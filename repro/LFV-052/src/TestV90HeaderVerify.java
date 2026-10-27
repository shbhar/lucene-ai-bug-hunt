import java.io.IOException;
import java.lang.reflect.Field;
import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.store.ByteArrayDataInput;
import org.apache.lucene.store.ByteBuffersDataOutput;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IntsRef;
import org.apache.lucene.util.fst.FST;
import org.apache.lucene.util.fst.FSTCompiler;
import org.apache.lucene.util.fst.PositiveIntOutputs;
import org.apache.lucene.util.fst.Util;

/**
 * Independent verification: does FSTMetadata.save honour FSTCompiler.Builder.setVersion?
 *
 * <p>Keys: 6 single-letter keys 'a'..'f' (contiguous labels at the root, depth 0, numArcs >= 5).
 * With VERSION_CURRENT the root is written as a continuous-arcs node; with VERSION_90 it must not
 * be (FSTCompiler.java:543).
 */
public class TestV90HeaderVerify extends LuceneTestCase {

  static final String KEYS = "abcdef";

  static final class Built {
    final FSTCompiler<Long> compiler;
    final FST<Long> fst;

    Built(FSTCompiler<Long> compiler, FST<Long> fst) {
      this.compiler = compiler;
      this.fst = fst;
    }
  }

  static Built build(Integer version) throws IOException {
    FSTCompiler.Builder<Long> b =
        new FSTCompiler.Builder<>(FST.INPUT_TYPE.BYTE1, PositiveIntOutputs.getSingleton());
    if (version != null) {
      b.setVersion(version);
    }
    FSTCompiler<Long> c = b.build();
    for (int i = 0; i < KEYS.length(); i++) {
      c.add(new IntsRef(new int[] {KEYS.charAt(i)}, 0, 1), (long) (i + 1));
    }
    FST<Long> fst = FST.fromFSTReader(c.compile(), c.getFSTReader());
    return new Built(c, fst);
  }

  static long counter(FSTCompiler<?> c, String name) throws Exception {
    Field f = FSTCompiler.class.getDeclaredField(name);
    f.setAccessible(true);
    return f.getLong(c);
  }

  static byte[][] save(FST<Long> fst) throws IOException {
    ByteBuffersDataOutput meta = new ByteBuffersDataOutput();
    ByteBuffersDataOutput data = new ByteBuffersDataOutput();
    fst.save(meta, data);
    return new byte[][] {meta.toArrayCopy(), data.toArrayCopy()};
  }

  /** Header layout (CodecUtil.writeHeader): BE magic(4) | vint len(1) "FST"(3) | BE version(4). */
  static int headerVersion(byte[] meta) {
    return ((meta[8] & 0xFF) << 24)
        | ((meta[9] & 0xFF) << 16)
        | ((meta[10] & 0xFF) << 8)
        | (meta[11] & 0xFF);
  }

  static void assertLookups(FST<Long> fst) throws IOException {
    for (int i = 0; i < KEYS.length(); i++) {
      assertEquals(
          Long.valueOf(i + 1), Util.get(fst, new IntsRef(new int[] {KEYS.charAt(i)}, 0, 1)));
    }
    assertNull(Util.get(fst, new IntsRef(new int[] {'g'}, 0, 1)));
  }

  /** Control: setVersion does change the in-memory version and the node encoding. PASSES. */
  public void test1_setVersionChangesEncodingAndInMemoryVersion() throws Exception {
    Built v90 = build(FST.VERSION_90);
    Built cur = build(null);
    assertEquals(8, v90.fst.getMetadata().getVersion());
    assertEquals(9, cur.fst.getMetadata().getVersion());
    assertEquals(0, counter(v90.compiler, "continuousNodeCount"));
    assertTrue(counter(cur.compiler, "continuousNodeCount") >= 1);
    assertLookups(v90.fst);
    assertLookups(cur.fst);
  }

  /** The candidate claim, part 1: header int written by save() for a VERSION_90 FST. */
  public void test2_savedHeaderVersionOfV90Fst() throws Exception {
    byte[][] md = save(build(FST.VERSION_90).fst);
    assertEquals("header version written for a VERSION_90 FST", 8, headerVersion(md[0]));
  }

  /** The candidate claim, part 2: getVersion() after save + readMetadata. */
  public void test3_roundTripVersionOfV90Fst() throws Exception {
    byte[][] md = save(build(FST.VERSION_90).fst);
    FST.FSTMetadata<Long> m =
        FST.readMetadata(new ByteArrayDataInput(md[0]), PositiveIntOutputs.getSingleton());
    FST<Long> f = new FST<>(m, new ByteArrayDataInput(md[1]));
    assertLookups(f);
    assertEquals("getVersion() after round trip", 8, m.getVersion());
  }

  /** The candidate claim, part 3: a [6,8] header check (what Lucene 9.0-9.8 does). */
  public void test4_v8HeaderCheckRejectsV90Fst() throws Exception {
    byte[][] md = save(build(FST.VERSION_90).fst);
    // Expected (if save honoured setVersion): returns 8. Bug: throws IndexFormatTooNewException.
    assertEquals(8, CodecUtil.checkHeader(new ByteArrayDataInput(md[0]), "FST", 6, 8));
  }

  /** Content is valid version-8 content: patch only the header int to 8 and everything works. */
  public void test5_headerPatchedTo8ReadsFine() throws Exception {
    byte[][] md = save(build(FST.VERSION_90).fst);
    md[0][11] = 8;
    assertEquals(8, CodecUtil.checkHeader(new ByteArrayDataInput(md[0]), "FST", 6, 8));
    FST.FSTMetadata<Long> m =
        FST.readMetadata(new ByteArrayDataInput(md[0]), PositiveIntOutputs.getSingleton());
    assertEquals(8, m.getVersion());
    assertLookups(new FST<>(m, new ByteArrayDataInput(md[1])));
  }

  /** Control: default (VERSION_CURRENT) FST: header 9, round trip 9. PASSES. */
  public void test6_defaultFstHeaderAndRoundTrip() throws Exception {
    byte[][] md = save(build(null).fst);
    assertEquals(9, headerVersion(md[0]));
    FST.FSTMetadata<Long> m =
        FST.readMetadata(new ByteArrayDataInput(md[0]), PositiveIntOutputs.getSingleton());
    assertEquals(9, m.getVersion());
    assertLookups(new FST<>(m, new ByteArrayDataInput(md[1])));
  }
}
