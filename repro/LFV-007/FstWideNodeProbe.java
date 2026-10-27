/*
 * Standalone probe (no test framework), written 2026-10-23 for the fst-expanded-size-corruption brief.
 * Same FST shape as TestFSTs.testOutputTooLargeForArrayArcs on branch fix-fst-expanded-size-overflow:
 * BYTE2 input, 2048 one-label keys 0, 32, 64, ..., 65504; key 0 has a (1<<20)-6 byte all-zero output, so
 * that arc is exactly 2^20 bytes (1 flags + 2 label + 3 vInt length + output); every other key has a
 * 1-byte output. 2048 * 2^20 = 2^31.
 *
 * It lives in package org.apache.lucene.util.fst only to read FSTCompiler's package-private encoding
 * counters (via reflection, so it also runs on releases where a field might be missing).
 *
 * args: [bigLen (default 1048570)] [allowFixedLengthArcs (default true)]
 *       [content: zero (default, like LuceneTestCase.newBytesRef(len)) | pattern (byte k = k*31+5)]
 *       [store: heap (default, FST.fromFSTReader) | offheap (save() then reload with OffHeapFSTStore)]
 */
package org.apache.lucene.util.fst;

import java.lang.reflect.Field;
import org.apache.lucene.store.ByteBuffersDataInput;
import org.apache.lucene.store.ByteBuffersDataOutput;
import org.apache.lucene.store.ByteBuffersIndexInput;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IntsRef;
import org.apache.lucene.util.IntsRefBuilder;
import org.apache.lucene.util.Version;

public class FstWideNodeProbe {
  static final int NUM_ARCS = 2048;
  static final int STRIDE = 32;

  static long counter(FSTCompiler<?> c, String name) {
    try {
      Field f = FSTCompiler.class.getDeclaredField(name);
      f.setAccessible(true);
      return f.getLong(c);
    } catch (ReflectiveOperationException e) {
      return -1;
    }
  }

  static BytesRef expected(int arc, byte[] big) {
    return arc == 0 ? new BytesRef(big) : new BytesRef(new byte[] {(byte) arc});
  }

  public static void main(String[] args) throws Exception {
    int bigLen = args.length > 0 ? Integer.parseInt(args[0]) : (1 << 20) - 6;
    boolean fixed = args.length > 1 ? Boolean.parseBoolean(args[1]) : true;
    boolean pattern = args.length > 2 && args[2].equals("pattern");
    boolean offheap = args.length > 3 && args[3].equals("offheap");
    boolean ea = false;
    assert ea = true;
    System.out.println(
        "lucene=" + Version.LATEST + " assertions=" + (ea ? "on(-ea)" : "off(-da)")
            + " bigLen=" + bigLen + " allowFixedLengthArcs=" + fixed
            + " content=" + (pattern ? "pattern" : "zero") + " store=" + (offheap ? "offheap" : "heap"));

    ByteSequenceOutputs outputs = ByteSequenceOutputs.getSingleton();
    FSTCompiler<BytesRef> c =
        new FSTCompiler.Builder<>(FST.INPUT_TYPE.BYTE2, outputs).allowFixedLengthArcs(fixed).build();
    byte[] big = new byte[bigLen];
    if (pattern) for (int k = 0; k < bigLen; k++) big[k] = (byte) (k * 31 + 5);
    IntsRefBuilder in = new IntsRefBuilder();
    in.append(0);
    for (int arc = 0; arc < NUM_ARCS; arc++) {
      in.setIntAt(0, arc * STRIDE);
      c.add(in.get(), expected(arc, big));
    }

    FST<BytesRef> fst;
    try {
      fst = FST.fromFSTReader(c.compile(), c.getFSTReader());
    } catch (Throwable t) {
      System.out.println("  compile() threw " + t);
      StackTraceElement[] st = t.getStackTrace();
      for (int i = 0; i < Math.min(2, st.length); i++) System.out.println("    at " + st[i]);
      return;
    }
    System.out.println(
        "  compile() returned normally; root node encoding counters: binarySearch="
            + counter(c, "binarySearchNodeCount")
            + " directAddressing=" + counter(c, "directAddressingNodeCount")
            + " continuous=" + counter(c, "continuousNodeCount")
            + "; fst.numBytes=" + fst.numBytes());
    if (offheap) {
      ByteBuffersDataOutput meta = new ByteBuffersDataOutput();
      ByteBuffersDataOutput data = new ByteBuffersDataOutput();
      fst.save(meta, data);
      ByteBuffersDataInput dataIn = data.toDataInput();
      FST.FSTMetadata<BytesRef> md = FST.readMetadata(meta.toDataInput(), outputs);
      fst = FST.fromFSTReader(md, new OffHeapFSTStore(new ByteBuffersIndexInput(dataIn, "fst"), 0, md));
      System.out.println("  saved with fst.save() and reloaded off-heap: " + data.size() + " data bytes");
    }

    int ok = 0, wrong = 0, threw = 0;
    String firstThrow = null;
    for (int arc = 0; arc < NUM_ARCS; arc++) {
      in.setIntAt(0, arc * STRIDE);
      try {
        BytesRef got = Util.get(fst, in.get());
        if (expected(arc, big).equals(got)) ok++;
        else wrong++;
      } catch (RuntimeException e) {
        threw++;
        if (firstThrow == null) firstThrow = e.toString();
      }
    }
    System.out.println(
        "  Util.get on the " + NUM_ARCS + " added keys: correct=" + ok + " wrong=" + wrong
            + " threw=" + threw + (firstThrow == null ? "" : " first=" + firstThrow));

    IntsRefFSTEnum<BytesRef> e = new IntsRefFSTEnum<>(fst);
    for (int step = 1; step <= 2; step++) {
      try {
        IntsRefFSTEnum.InputOutput<BytesRef> io = e.next();
        if (io == null) {
          System.out.println("  enum next() #" + step + " -> null (end)");
          break;
        }
        IntsRef k = io.input;
        String key = k.length == 1 ? Integer.toString(k.ints[k.offset]) : k.toString();
        boolean added =
            k.length == 1 && k.ints[k.offset] % STRIDE == 0 && k.ints[k.offset] < NUM_ARCS * STRIDE;
        System.out.println(
            "  enum next() #" + step + " -> key=" + key + " (added: " + added + ") outputLength="
                + io.output.length);
      } catch (RuntimeException ex) {
        System.out.println("  enum next() #" + step + " threw " + ex);
        break;
      }
    }
  }
}
