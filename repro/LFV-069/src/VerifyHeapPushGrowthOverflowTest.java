import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.ArrayUtil;
import org.apache.lucene.util.LongHeap;
import org.apache.lucene.util.TernaryLongHeap;

/**
 * Independent check of: LongHeap.push / TernaryLongHeap.push compute the growth target as
 * {@code (size * 3 + 1) / 2} in int, which wraps once size >= 715,827,883.
 *
 * <p>All heavy tests build the heap with the public (size, initialValue) constructor, which gives
 * heap.length == size + 1, so the very next push() is the one that has to grow the array.
 */
public class VerifyHeapPushGrowthOverflowTest extends LuceneTestCase {

  /** First heap.length for which the int growth target is wrong (from the arithmetic test). */
  static final int BAD_LEN = 715_827_883;

  static boolean assertsOn() {
    return ArrayUtil.class.desiredAssertionStatus();
  }

  // ---------------------------------------------------------------- cheap controls

  /** Control: ordinary growth works (both heaps), values come back sorted. */
  public void testSmallHeapsGrowNormally() {
    LongHeap h2 = new LongHeap(1);
    TernaryLongHeap h3 = new TernaryLongHeap(1);
    int n = 100_000;
    for (int i = 0; i < n; i++) {
      long v = random().nextLong();
      h2.push(v);
      h3.push(v);
    }
    assertEquals(n, h2.size());
    assertEquals(n, h3.size());
    long prev2 = Long.MIN_VALUE, prev3 = Long.MIN_VALUE;
    for (int i = 0; i < n; i++) {
      long a = h2.pop(), b = h3.pop();
      assertTrue(a >= prev2);
      assertTrue(b >= prev3);
      assertEquals(a, b);
      prev2 = a;
      prev3 = b;
    }
  }

  /**
   * Pure arithmetic, exhaustive over every reachable heap.length L in [2, MAX_ARRAY_LENGTH - 1]:
   * push() only enlarges the array when the int target (L*3+1)/2 is > L (ArrayUtil.grow returns
   * the same array when array.length >= minSize). Same expression/types as LongHeap.java:79.
   */
  public void testGrowthTargetExhaustive() {
    int firstBad = -1;
    long bad = 0, badNegative = 0, badNonNegative = 0;
    for (int len = 2; len < ArrayUtil.MAX_ARRAY_LENGTH; len++) {
      int target = (len * 3 + 1) / 2; // as in LongHeap.push, int arithmetic
      if (target <= len) {
        if (firstBad < 0) firstBad = len;
        bad++;
        if (target < 0) badNegative++;
        else badNonNegative++;
      } else {
        assertEquals("no wrap expected below the first bad length", (len * 3L + 1) / 2, target);
      }
    }
    System.out.println(
        "ARITH firstBad="
            + firstBad
            + " bad="
            + bad
            + " (negative="
            + badNegative
            + ", nonNegative-but-too-small="
            + badNonNegative
            + ") MAX_ARRAY_LENGTH="
            + ArrayUtil.MAX_ARRAY_LENGTH);
    assertEquals(BAD_LEN, firstBad);
    // every length from BAD_LEN up to MAX_ARRAY_LENGTH-1 is bad: no "recovery" above the threshold
    assertEquals((long) ArrayUtil.MAX_ARRAY_LENGTH - BAD_LEN, bad);
    // exact (long) target at the boundary fits easily in an int and in an array
    long exact = (BAD_LEN * 3L + 1) / 2;
    assertTrue(exact <= ArrayUtil.MAX_ARRAY_LENGTH);
    System.out.println(
        "ARITH at len=" + BAD_LEN + ": int target=" + ((BAD_LEN * 3 + 1) / 2) + " exact=" + exact);
  }

  /**
   * Where does a heap that is grown only by push() (no big initial capacity) get stuck? Uses the
   * real ArrayUtil.oversize for the growth steps.
   */
  public void testGrowthChainCaps() {
    for (int initialCapacity : new int[] {1, 10, 100, 1000, 16384}) {
      int len = initialCapacity + 1;
      while (true) {
        int target = (len * 3 + 1) / 2;
        if (target <= len) break; // the push at size == len will fail
        len = ArrayUtil.oversize(target, Long.BYTES);
      }
      System.out.println(
          "CHAIN initialCapacity="
              + initialCapacity
              + " -> stuck at heap.length="
              + len
              + ", max elements="
              + (len - 1)
              + " (MAX_ARRAY_LENGTH-1="
              + (ArrayUtil.MAX_ARRAY_LENGTH - 1)
              + ")");
      assertTrue(len >= BAD_LEN && len < ArrayUtil.MAX_ARRAY_LENGTH);
    }
  }

  // ---------------------------------------------------------------- heavy tests (5.33 GiB each)

  private static void checkFailure(Throwable t, String who) {
    System.out.println(who + " push threw: " + t);
    if (assertsOn()) {
      assertTrue(who + ": " + t, t instanceof AssertionError);
      assertTrue(
          who + ": " + t.getMessage(), t.getMessage().contains("got " + ((BAD_LEN * 3 + 1) / 2)));
    } else {
      assertTrue(who + ": " + t, t instanceof ArrayIndexOutOfBoundsException);
      assertEquals(
          "Index " + BAD_LEN + " out of bounds for length " + BAD_LEN, t.getMessage());
    }
  }

  /** The bug: heap.length == 715,827,883, push() must grow but does not. */
  public void testLongHeapPushAtBoundary() {
    LongHeap heap = new LongHeap(BAD_LEN - 1, 0L); // long[715_827_883], 5.33 GiB
    assertEquals(BAD_LEN - 1, heap.size());
    Throwable t = expectThrows(Throwable.class, () -> heap.push(1L));
    checkFailure(t, "LongHeap");
    // side effect: size was incremented before the failure, the heap is now inconsistent
    System.out.println("LongHeap size after failed push=" + heap.size());
    assertEquals(BAD_LEN, heap.size());
    Throwable t2 = expectThrows(Throwable.class, heap::pop);
    System.out.println("LongHeap pop after failed push threw: " + t2);
    assertTrue(t2 instanceof ArrayIndexOutOfBoundsException);
  }

  /** Same code copied into TernaryLongHeap (used by NeighborQueue / HNSW). */
  public void testTernaryLongHeapPushAtBoundary() {
    TernaryLongHeap heap = new TernaryLongHeap(BAD_LEN - 1, 0L);
    assertEquals(BAD_LEN - 1, heap.size());
    Throwable t = expectThrows(Throwable.class, () -> heap.push(1L));
    checkFailure(t, "TernaryLongHeap");
    System.out.println("TernaryLongHeap size after failed push=" + heap.size());
    assertEquals(BAD_LEN, heap.size());
  }

  /**
   * Control, one element below the boundary: heap.length == 715,827,882. push() computes a valid
   * target (1,073,741,823) and really tries to allocate the bigger array; with -Xmx8g that
   * allocation (about 9.7 GB) cannot succeed, so the honest outcome is OutOfMemoryError, i.e. the
   * normal "not enough heap" failure, not a logic error.
   */
  public void testLongHeapPushOneBelowBoundaryTriesToGrow() {
    int len = BAD_LEN - 1;
    int target = (len * 3 + 1) / 2;
    int newLen = ArrayUtil.oversize(target, Long.BYTES);
    System.out.println(
        "CONTROL len=" + len + " target=" + target + " oversize=" + newLen
            + " bytes=" + (8L * newLen) + " maxMemory=" + Runtime.getRuntime().maxMemory());
    assumeTrue(
        "control needs a heap too small for the grown array",
        8L * newLen > Runtime.getRuntime().maxMemory());
    LongHeap heap = new LongHeap(len - 1, 0L); // long[715_827_882]
    Throwable t = expectThrows(Throwable.class, () -> heap.push(1L));
    System.out.println("CONTROL LongHeap push threw: " + t);
    assertTrue(t.toString(), t instanceof OutOfMemoryError);
  }
}
