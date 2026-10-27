import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDataInput;
import org.apache.lucene.store.ByteBuffersDataOutput;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.store.SingleInstanceLockFactory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * Independent verification of the finding: ByteBuffersDataInput bulk readLongs/readFloats with
 * blocks smaller than 8 (longs) / 4 (floats) bytes.
 *
 * <p>Oracle: the per-value readLong()/readInt() path of the very same ByteBuffersDataInput, and the
 * values that were written. All inputs are deterministic (no random()).
 */
public class TestBBDISmallBlockBulkReads extends LuceneTestCase {

  private static final long[] LONGS = {
    0x0102030405060708L, -1L, Long.MIN_VALUE, Long.MAX_VALUE, 42L, 0L, -42L, 0x1122334455667788L
  };
  private static final float[] FLOATS = {1.5f, -2.25f, Float.MAX_VALUE, Float.MIN_VALUE, 0f, 3f, -0f, 7e7f};

  private static ByteBuffersDataOutput out(int bits) {
    return new ByteBuffersDataOutput(
        bits, bits, ByteBuffersDataOutput.ALLOCATE_BB_ON_HEAP, ByteBuffersDataOutput.NO_REUSE);
  }

  /** Returns null on success, else a description of the failure. */
  private static String tryReadLongs(int bits, int prefix) throws IOException {
    ByteBuffersDataOutput o = out(bits);
    for (int i = 0; i < prefix; i++) o.writeByte((byte) 0x55);
    for (long v : LONGS) o.writeLong(v);
    ByteBuffersDataInput in = o.toDataInput();
    in.skipBytes(prefix);
    long[] got = new long[LONGS.length];
    try {
      in.readLongs(got, 0, got.length);
    } catch (RuntimeException e) {
      return e.getClass().getSimpleName() + ": " + e.getMessage();
    }
    for (int i = 0; i < got.length; i++) {
      if (got[i] != LONGS[i]) return "wrong value at " + i + ": " + got[i];
    }
    if (in.position() != prefix + 8L * LONGS.length) return "wrong position " + in.position();
    return null;
  }

  private static String tryReadFloats(int bits, int prefix) throws IOException {
    ByteBuffersDataOutput o = out(bits);
    for (int i = 0; i < prefix; i++) o.writeByte((byte) 0x55);
    for (float v : FLOATS) o.writeInt(Float.floatToIntBits(v));
    ByteBuffersDataInput in = o.toDataInput();
    in.skipBytes(prefix);
    float[] got = new float[FLOATS.length];
    try {
      in.readFloats(got, 0, got.length);
    } catch (RuntimeException e) {
      return e.getClass().getSimpleName() + ": " + e.getMessage();
    }
    for (int i = 0; i < got.length; i++) {
      if (Float.floatToIntBits(got[i]) != Float.floatToIntBits(FLOATS[i]))
        return "wrong value at " + i + ": " + got[i];
    }
    return null;
  }

  /** Matrix over block size (2..32 bytes) x misalignment prefix (0..7). Prints and asserts. */
  public void testReadLongsMatrix() throws IOException {
    List<String> failures = new ArrayList<>();
    for (int bits = ByteBuffersDataOutput.LIMIT_MIN_BITS_PER_BLOCK; bits <= 5; bits++) {
      StringBuilder row = new StringBuilder("readLongs  block=" + (1 << bits) + "B :");
      for (int prefix = 0; prefix < 8; prefix++) {
        String r = tryReadLongs(bits, prefix);
        row.append(r == null ? " ok " : " EXC");
        if (r != null) failures.add("block=" + (1 << bits) + " prefix=" + prefix + " -> " + r);
      }
      System.out.println(row);
    }
    failures.forEach(f -> System.out.println("  " + f));
    assertTrue("readLongs failures: " + failures.size() + " first=" + (failures.isEmpty() ? "" : failures.get(0)), failures.isEmpty());
  }

  public void testReadFloatsMatrix() throws IOException {
    List<String> failures = new ArrayList<>();
    for (int bits = ByteBuffersDataOutput.LIMIT_MIN_BITS_PER_BLOCK; bits <= 5; bits++) {
      StringBuilder row = new StringBuilder("readFloats block=" + (1 << bits) + "B :");
      for (int prefix = 0; prefix < 8; prefix++) {
        String r = tryReadFloats(bits, prefix);
        row.append(r == null ? " ok " : " EXC");
        if (r != null) failures.add("block=" + (1 << bits) + " prefix=" + prefix + " -> " + r);
      }
      System.out.println(row);
    }
    failures.forEach(f -> System.out.println("  " + f));
    assertTrue("readFloats failures: " + failures.size() + " first=" + (failures.isEmpty() ? "" : failures.get(0)), failures.isEmpty());
  }

  /** Control: the per-value paths and readInts on the same tiny-block inputs work. */
  public void testControlPerValueReadsWork() throws IOException {
    for (int bits = 1; bits <= 5; bits++) {
      for (int prefix = 0; prefix < 8; prefix++) {
        ByteBuffersDataOutput o = out(bits);
        for (int i = 0; i < prefix; i++) o.writeByte((byte) 0x55);
        for (long v : LONGS) o.writeLong(v);
        ByteBuffersDataInput in = o.toDataInput();
        in.skipBytes(prefix);
        for (long v : LONGS) assertEquals(v, in.readLong());
        for (int i = 0; i < LONGS.length; i++)
          assertEquals(LONGS[i], in.readLong(prefix + 8L * i)); // RandomAccessInput
        in.seek(prefix);
        int[] ints = new int[2 * LONGS.length];
        in.readInts(ints, 0, ints.length); // DataInput default loop over readInt()
        for (int i = 0; i < LONGS.length; i++) {
          long v = (ints[2 * i] & 0xFFFFFFFFL) | ((long) ints[2 * i + 1] << 32);
          assertEquals(LONGS[i], v);
        }
      }
    }
  }

  /** Same defect through the public List&lt;ByteBuffer&gt; constructor (4-byte power-of-two pages). */
  public void testListConstructorFourBytePages() throws IOException {
    ByteBuffer all = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
    all.put(new byte[] {9, 9, 9, 9, 9}).putLong(LONGS[0]).putLong(LONGS[1]); // 21 bytes, 5-byte prefix
    List<ByteBuffer> pages = new ArrayList<>();
    for (int i = 0; i < 24; i += 4) pages.add(ByteBuffer.wrap(all.array(), i, 4).slice());
    ByteBuffersDataInput in = new ByteBuffersDataInput(pages);
    in.skipBytes(5);
    assertEquals(LONGS[0], in.readLong()); // per-value control works
    in.seek(5);
    long[] got = new long[2];
    in.readLongs(got, 0, 2); // expected to work; candidate says IAE
    assertArrayEquals(new long[] {LONGS[0], LONGS[1]}, got);
  }

  /**
   * End-to-end reachability: an index in a ByteBuffersDirectory whose files use 2-byte blocks
   * (the same construction TestMultiByteBuffersDirectory uses for maxChunkSize=2). Deleting a doc
   * makes the reader load live docs via IndexInput.readLongs (Lucene90LiveDocsFormat).
   */
  public void testEndToEndLiveDocsTinyBlocks() throws IOException {
    for (int bits : new int[] {1, 2, 3}) {
      Supplier<ByteBuffersDataOutput> sup = () -> out(bits);
      String outcome;
      try (ByteBuffersDirectory dir =
          new ByteBuffersDirectory(
              new SingleInstanceLockFactory(), sup, ByteBuffersDirectory.OUTPUT_AS_MANY_BUFFERS)) {
        IndexWriterConfig iwc =
            new IndexWriterConfig()
                .setCodec(TestUtil.getDefaultCodec())
                .setMergePolicy(NoMergePolicy.INSTANCE)
                .setUseCompoundFile(false);
        try (IndexWriter w = new IndexWriter(dir, iwc)) {
          for (int i = 0; i < 200; i++) {
            Document d = new Document();
            d.add(new StringField("id", Integer.toString(i), Field.Store.NO));
            w.addDocument(d);
          }
          w.commit();
          w.deleteDocuments(new Term("id", "7"));
          w.commit();
        }
        try (DirectoryReader r = DirectoryReader.open(dir)) {
          outcome = "ok numDocs=" + r.numDocs();
          assertEquals(199, r.numDocs());
        } catch (RuntimeException e) {
          outcome = e.getClass().getSimpleName() + ": " + e.getMessage();
          StackTraceElement[] st = e.getStackTrace();
          for (int k = 0; k < Math.min(12, st.length); k++) System.out.println("    at " + st[k]);
        }
      }
      System.out.println("end-to-end block=" + (1 << bits) + "B -> " + outcome);
      assertTrue("block=" + (1 << bits) + "B: " + outcome, outcome.startsWith("ok"));
    }
  }

  /** IndexInput-level (ByteBuffersIndexInput delegates readLongs to ByteBuffersDataInput). */
  public void testIndexInputReadLongsTwoByteBlocks() throws IOException {
    try (ByteBuffersDirectory dir =
        new ByteBuffersDirectory(
            new SingleInstanceLockFactory(), () -> out(1), ByteBuffersDirectory.OUTPUT_AS_MANY_BUFFERS)) {
      try (IndexOutput o = dir.createOutput("f", IOContext.DEFAULT)) {
        o.writeByte((byte) 2);
        o.writeByte((byte) 2);
        o.writeByte((byte) 2);
        o.writeLong(3L);
        o.writeLong(Long.MAX_VALUE);
        o.writeLong(-3L);
      }
      try (IndexInput in = dir.openInput("f", IOContext.DEFAULT)) {
        in.seek(3);
        long[] l = new long[4];
        in.readLongs(l, 1, 3);
        assertArrayEquals(new long[] {0L, 3L, Long.MAX_VALUE, -3L}, l);
      }
    }
  }

  /**
   * Differential control: the same IndexInput.readLongs/readFloats calls on an MMapDirectory with
   * 2-byte chunks (also a supported configuration, used by TestMultiMMap) return the right values.
   */
  public void testControlMMapTwoByteChunks() throws IOException {
    try (MMapDirectory dir = new MMapDirectory(createTempDir("mmap2"), 2)) {
      try (IndexOutput o = dir.createOutput("f", IOContext.DEFAULT)) {
        o.writeByte((byte) 2);
        o.writeByte((byte) 2);
        o.writeByte((byte) 2);
        o.writeLong(3L);
        o.writeLong(Long.MAX_VALUE);
        o.writeLong(-3L);
        o.writeInt(Float.floatToIntBits(1.5f));
        o.writeInt(Float.floatToIntBits(-2.25f));
      }
      try (IndexInput in = dir.openInput("f", IOContext.DEFAULT)) {
        in.seek(3);
        long[] l = new long[4];
        in.readLongs(l, 1, 3);
        assertArrayEquals(new long[] {0L, 3L, Long.MAX_VALUE, -3L}, l);
        float[] f = new float[2];
        in.readFloats(f, 0, 2); // file pointer 27: 27 & 3 == 3 (the failing float alignment)
        assertEquals(1.5f, f[0], 0f);
        assertEquals(-2.25f, f[1], 0f);
      }
    }
  }
}
