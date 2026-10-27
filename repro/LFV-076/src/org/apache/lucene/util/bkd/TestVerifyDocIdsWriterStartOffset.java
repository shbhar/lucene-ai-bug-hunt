package org.apache.lucene.util.bkd;

import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import java.util.TreeMap;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent verification (verify, candidate 7): DocIdsWriter.writeDocIds(int[] docIds, int
 * start, int count, DataOutput out) must encode exactly docIds[start .. start+count) so that
 * readInts(in, count, out) returns that slice. Every case is deterministic (fixed arrays or a
 * fixed java.util.Random seed); LuceneTestCase's randomness is not used.
 *
 * <p>Each "bug" test asserts the CORRECT (expected) behavior and so FAILS on main 7cecf79523 if
 * the defect is present. Each "control" test checks the same slice copied to offset 0, which must
 * pass.
 */
public class TestVerifyDocIdsWriterStartOffset extends LuceneTestCase {

  /** Encoding tag written as the first byte (mirrors DocIdsWriter's private constants). */
  private static String tagName(byte b) {
    switch (b) {
      case -2:
        return "CONTINUOUS_IDS";
      case -1:
        return "BITSET_IDS";
      case 16:
        return "DELTA_BPV_16";
      case 21:
        return "BPV_21";
      case 24:
        return "BPV_24";
      case 32:
        return "BPV_32";
      default:
        return "tag" + b;
    }
  }

  private static final class Encoded {
    final byte tag;
    final int[] decoded;
    final long bytesWritten;
    final long bytesRead;

    Encoded(byte tag, int[] decoded, long bytesWritten, long bytesRead) {
      this.tag = tag;
      this.decoded = decoded;
      this.bytesWritten = bytesWritten;
      this.bytesRead = bytesRead;
    }
  }

  private static int fileCounter = 0;

  /** Write docIds[start..start+count) with writeDocIds and read it back with readInts. */
  private static Encoded roundTrip(
      Directory dir, int version, int maxPointsInLeaf, int[] docIds, int start, int count)
      throws IOException {
    DocIdsWriter w = new DocIdsWriter(maxPointsInLeaf, version);
    String name = "f" + (fileCounter++);
    long written;
    try (IndexOutput out = dir.createOutput(name, IOContext.DEFAULT)) {
      w.writeDocIds(docIds, start, count, out);
      written = out.getFilePointer();
    }
    byte tag;
    try (IndexInput in = dir.openInput(name, IOContext.DEFAULT)) {
      tag = in.readByte();
    }
    int[] read = new int[count];
    long readFP;
    try (IndexInput in = dir.openInput(name, IOContext.DEFAULT)) {
      // fresh reader instance, same as BKDReader does
      new DocIdsWriter(maxPointsInLeaf, version).readInts(in, count, read);
      readFP = in.getFilePointer();
    }
    dir.deleteFile(name);
    return new Encoded(tag, read, written, readFP);
  }

  private static void report(String label, int[] arr, int start, int count, Encoded e) {
    int[] expected = Arrays.copyOfRange(arr, start, start + count);
    System.out.println(
        label
            + ": input="
            + Arrays.toString(arr)
            + " start="
            + start
            + " count="
            + count
            + " tag="
            + tagName(e.tag)
            + " expected="
            + (count <= 8 ? Arrays.toString(expected) : ("<" + count + " ints>"))
            + " decoded="
            + (count <= 8 ? Arrays.toString(e.decoded) : ("<" + count + " ints>"))
            + " equal="
            + Arrays.equals(expected, e.decoded));
  }

  // ---------------------------------------------------------------------------------------------
  // Bug 1: BPV_21 packed-long loop reads docIds[i] instead of docIds[start + i]
  // (DocIdsWriter.java:135-138 on 7cecf79523)
  // ---------------------------------------------------------------------------------------------

  public void testBug1_bpv21SmallBlockStart1() throws IOException {
    // not strictly sorted (100000 > 5), in-block range 5..200000 > 0xFFFF, max <= 0x1FFFFF
    // -> BPV_21. count=3 -> oneThird = floorToMultipleOf16(1) = 0, so the whole block goes through
    // the packed-long loop.
    int[] arr = {7, 100000, 5, 200000};
    try (Directory dir = new ByteBuffersDirectory()) {
      Encoded e = roundTrip(dir, BKDWriter.VERSION_CURRENT, 512, arr, 1, 3);
      report("bug1 small", arr, 1, 3, e);
      assertEquals("BPV_21", tagName(e.tag));
      assertEquals(e.bytesWritten, e.bytesRead);
      assertArrayEquals(new int[] {100000, 5, 200000}, e.decoded);
    }
  }

  public void testBug1_bpv21DefaultLeafSizeStart1() throws IOException {
    // count = 512 (BKDConfig.DEFAULT_MAX_POINTS_IN_LEAF_NODE). oneThird = floor16(170) = 160,
    // numInts = 320, so positions 480..509 go through the packed-long loop.
    final int count = 512;
    int[] arr = new int[count + 1];
    Random r = new Random(0xC0FFEEL);
    arr[0] = 1_000_000; // a value outside the slice, chosen so min/max seeding is harmless here
    for (int i = 1; i <= count; i++) {
      arr[i] = 100_000 + r.nextInt(1_000_000); // in [100000, 1100000), max <= 0x1FFFFF
    }
    // make sure docIds[start] is neither the slice's min nor its max, so only bug 1 matters
    int[] slice = Arrays.copyOfRange(arr, 1, 1 + count);
    int min = Arrays.stream(slice).min().getAsInt();
    int max = Arrays.stream(slice).max().getAsInt();
    assertTrue(arr[1] != min && arr[1] != max);
    assertTrue(arr[0] >= min && arr[0] <= max);
    try (Directory dir = new ByteBuffersDirectory()) {
      Encoded e = roundTrip(dir, BKDWriter.VERSION_CURRENT, 512, arr, 1, count);
      assertEquals("BPV_21", tagName(e.tag));
      int firstBad = -1;
      int numBad = 0;
      for (int i = 0; i < count; i++) {
        if (slice[i] != e.decoded[i]) {
          if (firstBad < 0) firstBad = i;
          numBad++;
        }
      }
      System.out.println(
          "bug1 512: mismatching positions="
              + numBad
              + " firstBad="
              + firstBad
              + (firstBad >= 0
                  ? (" expected=" + slice[firstBad] + " decoded=" + e.decoded[firstBad])
                  : ""));
      assertArrayEquals(slice, e.decoded);
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Bug 2: min/max seeded from docIds[0] instead of docIds[start]
  // (DocIdsWriter.java:72-73 on 7cecf79523). The loop at :74 starts at i=1 and reads
  // docIds[start+i], so docIds[start] never contributes to min/max when start > 0.
  // ---------------------------------------------------------------------------------------------

  public void testBug2_seedMisdetectsContinuous() throws IOException {
    // slice {100, 50001, 50002} is strictly sorted with range 49903; but min/max are computed over
    // {docIds[0]=50000, 50001, 50002} -> min2max = 3 == count -> CONTINUOUS_IDS from docIds[start].
    int[] arr = {50000, 100, 50001, 50002};
    try (Directory dir = new ByteBuffersDirectory()) {
      Encoded e = roundTrip(dir, BKDWriter.VERSION_CURRENT, 512, arr, 1, 3);
      report("bug2 continuous", arr, 1, 3, e);
      assertArrayEquals(new int[] {100, 50001, 50002}, e.decoded);
    }
  }

  public void testBug2_seedWrongDelta16Base() throws IOException {
    // slice {10, 1005, 1003}: not sorted; min/max over {1000, 1005, 1003} = 1000..1005, so the
    // DELTA_BPV_16 base is 1000 and 10 - 1000 is negative -> garbage after decoding.
    int[] arr = {1000, 10, 1005, 1003};
    try (Directory dir = new ByteBuffersDirectory()) {
      Encoded e = roundTrip(dir, BKDWriter.VERSION_CURRENT, 512, arr, 1, 3);
      report("bug2 delta16", arr, 1, 3, e);
      assertArrayEquals(new int[] {10, 1005, 1003}, e.decoded);
    }
  }

  public void testBug2_seedOldVersion9() throws IOException {
    // Same defect in the version-9 (pre-BPV_21) write path.
    int[] arr = {1000, 10, 1005, 1003};
    try (Directory dir = new ByteBuffersDirectory()) {
      Encoded e = roundTrip(dir, BKDWriter.VERSION_META_FILE, 512, arr, 1, 3);
      report("bug2 delta16 v9", arr, 1, 3, e);
      assertArrayEquals(new int[] {10, 1005, 1003}, e.decoded);
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Controls: the identical slices at start = 0 round-trip correctly.
  // ---------------------------------------------------------------------------------------------

  public void testControl_sameSlicesAtStart0() throws IOException {
    int[][] arrs = {
      {7, 100000, 5, 200000}, {50000, 100, 50001, 50002}, {1000, 10, 1005, 1003},
    };
    try (Directory dir = new ByteBuffersDirectory()) {
      for (int[] arr : arrs) {
        int[] slice = Arrays.copyOfRange(arr, 1, 4);
        for (int version : new int[] {BKDWriter.VERSION_META_FILE, BKDWriter.VERSION_CURRENT}) {
          Encoded e = roundTrip(dir, version, 512, slice, 0, 3);
          report("control v" + version, slice, 0, 3, e);
          assertArrayEquals(slice, e.decoded);
          assertEquals(e.bytesWritten, e.bytesRead);
        }
      }
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Deterministic sweep: for many slices, compare writeDocIds(arr, start, count) to the
  // ground truth arr[start..start+count). start=0 must never fail; start>0 failure rate is
  // reported per encoding chosen by the start=0 encoder for the same slice.
  // ---------------------------------------------------------------------------------------------

  public void testSweep() throws IOException {
    Random r = new Random(20261022L);
    TreeMap<String, int[]> stats = new TreeMap<>(); // key -> {cases, failures}
    int start0Failures = 0;
    try (Directory dir = new ByteBuffersDirectory()) {
      for (int iter = 0; iter < 20000; iter++) {
        int count = 1 + r.nextInt(r.nextBoolean() ? 60 : 512);
        int start = r.nextInt(4); // 0..3
        int version = r.nextBoolean() ? BKDWriter.VERSION_META_FILE : BKDWriter.VERSION_CURRENT;
        int kind = r.nextInt(6);
        int[] arr = new int[start + count + 2];
        int base = r.nextInt(1 << 22);
        for (int i = 0; i < arr.length; i++) {
          switch (kind) {
            case 0: // continuous
              arr[i] = base + i;
              break;
            case 1: // sorted, dense (bitset)
              arr[i] = (i == 0 ? base : arr[i - 1]) + 1 + r.nextInt(8);
              break;
            case 2: // random narrow (delta16)
              arr[i] = base + r.nextInt(60000);
              break;
            case 3: // random <= 21 bits
              arr[i] = r.nextInt(1 << 21);
              break;
            case 4: // random <= 24 bits
              arr[i] = r.nextInt(1 << 24);
              break;
            default: // random < MAX_DOCS
              arr[i] = r.nextInt(org.apache.lucene.index.IndexWriter.MAX_DOCS);
              break;
          }
        }
        int[] slice = Arrays.copyOfRange(arr, start, start + count);
        Encoded truth = roundTrip(dir, version, 512, slice, 0, count);
        if (!Arrays.equals(slice, truth.decoded)) {
          start0Failures++;
          continue;
        }
        Encoded e = roundTrip(dir, version, 512, arr, start, count);
        boolean ok = Arrays.equals(slice, e.decoded);
        String key =
            "start" + (start == 0 ? "=0" : ">0") + " v" + version + " " + tagName(truth.tag);
        int[] s = stats.computeIfAbsent(key, k -> new int[2]);
        s[0]++;
        if (!ok) s[1]++;
      }
    }
    System.out.println("sweep: start=0 control failures = " + start0Failures);
    for (var en : stats.entrySet()) {
      System.out.println(
          "sweep: "
              + en.getKey()
              + " cases="
              + en.getValue()[0]
              + " wrong_decodes="
              + en.getValue()[1]);
    }
    assertEquals(0, start0Failures);
    for (var en : stats.entrySet()) {
      if (en.getKey().startsWith("start=0")) {
        assertEquals(en.getKey(), 0, en.getValue()[1]);
      }
    }
  }
}
