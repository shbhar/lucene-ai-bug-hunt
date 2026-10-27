import java.util.function.IntSupplier;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.join.BlockJoinSelector;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BitSet;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.FixedBitSet;
import org.apache.lucene.util.SparseFixedBitSet;

/**
 * Independent verification of the finding: BitSet#nextSetBit(start, end) on an empty range
 * (start == end).
 *
 * <p>Every test asserts what the javadoc of BitSet#nextSetBit(int, int) promises ("Returns the
 * index of the first set bit from start (inclusive) until end (exclusive). NO_MORE_DOCS is returned
 * if there are no more set bits."). A failing test therefore marks a discrepancy between the code
 * and that sentence. Each probe also prints one OUTCOME line, so the run log can be read as a table.
 */
public class TestVerifyNextSetBitEmptyRange extends LuceneTestCase {

  private static final int NMD = DocIdSetIterator.NO_MORE_DOCS;

  private static int probe(String name, IntSupplier call) {
    try {
      int r = call.getAsInt();
      System.out.println(
          "OUTCOME " + name + " -> returned " + (r == NMD ? "NO_MORE_DOCS" : Integer.toString(r)));
      return r;
    } catch (Throwable t) {
      System.out.println("OUTCOME " + name + " -> threw " + t.getClass().getName() + ": " + t.getMessage());
      throw t;
    }
  }

  // ---------- FixedBitSet ----------

  /** Empty range strictly inside the set; a set bit sits exactly at start. */
  public void testFixedEmptyRangeInterior() {
    FixedBitSet f = new FixedBitSet(10);
    f.set(5);
    assertEquals(NMD, probe("FixedBitSet(10){5}.nextSetBit(5,5)", () -> f.nextSetBit(5, 5)));
  }

  /** Empty range at length, length not a multiple of 64. */
  public void testFixedEmptyRangeAtLengthUnaligned() {
    FixedBitSet f = new FixedBitSet(3);
    assertEquals(NMD, probe("FixedBitSet(3){}.nextSetBit(3,3)", () -> f.nextSetBit(3, 3)));
  }

  /** Empty range at length, length a multiple of 64 (start indexes one word past the array). */
  public void testFixedEmptyRangeAtLengthWordAligned() {
    FixedBitSet f = new FixedBitSet(64);
    assertEquals(NMD, probe("FixedBitSet(64){}.nextSetBit(64,64)", () -> f.nextSetBit(64, 64)));
  }

  // ---------- SparseFixedBitSet ----------

  public void testSparseEmptyRangeInterior() {
    SparseFixedBitSet s = new SparseFixedBitSet(10);
    s.set(5);
    assertEquals(NMD, probe("SparseFixedBitSet(10){5}.nextSetBit(5,5)", () -> s.nextSetBit(5, 5)));
  }

  public void testSparseEmptyRangeAtLengthUnaligned() {
    SparseFixedBitSet s = new SparseFixedBitSet(3);
    assertEquals(NMD, probe("SparseFixedBitSet(3){}.nextSetBit(3,3)", () -> s.nextSetBit(3, 3)));
  }

  /** length a multiple of 4096: start indexes one block past the indices array. */
  public void testSparseEmptyRangeAtLengthBlockAligned() {
    SparseFixedBitSet s = new SparseFixedBitSet(4096);
    assertEquals(
        NMD, probe("SparseFixedBitSet(4096){}.nextSetBit(4096,4096)", () -> s.nextSetBit(4096, 4096)));
  }

  // ---------- Controls (must pass in every configuration) ----------

  public void testControlNonEmptyRanges() {
    for (BitSet b : new BitSet[] {new FixedBitSet(10), new SparseFixedBitSet(10)}) {
      b.set(5);
      String n = b.getClass().getSimpleName() + "(10){5}";
      assertEquals(5, probe(n + ".nextSetBit(5,6)", () -> b.nextSetBit(5, 6)));
      assertEquals(5, probe(n + ".nextSetBit(0,10)", () -> b.nextSetBit(0, 10)));
      assertEquals(NMD, probe(n + ".nextSetBit(4,5)", () -> b.nextSetBit(4, 5)));
      assertEquals(NMD, probe(n + ".nextSetBit(6,10)", () -> b.nextSetBit(6, 10)));
      assertEquals(NMD, probe(n + ".nextSetBit(9,10)", () -> b.nextSetBit(9, 10)));
    }
  }

  /** Oracle contrast only: java.util.BitSet accepts fromIndex == size and returns -1. */
  public void testControlJdkBitSet() {
    java.util.BitSet j = new java.util.BitSet(64);
    assertEquals(-1, probe("java.util.BitSet(64){}.nextSetBit(64)", () -> j.nextSetBit(64)));
  }

  // ---------- Context: the single-arg overload has the same precondition ----------

  /** BitSet#nextSetBit(int) javadoc also says NO_MORE_DOCS "if there are no more set bits". */
  public void testSingleArgAtLength() {
    FixedBitSet f = new FixedBitSet(64);
    assertEquals(NMD, probe("FixedBitSet(64){}.nextSetBit(64)", () -> f.nextSetBit(64)));
  }

  // ---------- Reachability from a Lucene caller ----------

  /**
   * BlockJoinSelector.wrap(Bits, BitSet, BitSet).get(parent) iterates children with
   * nextSetBit(child + 1, parent). When the parent's last child is at parent - 1 and no child has
   * a value, the loop's final call is nextSetBit(parent, parent): an empty range.
   *
   * <p>Docs: 0 = child, 1 = child, 2 = child, 3 = parent. No child has a value, so get(3) must be
   * false.
   */
  public void testBlockJoinSelectorBitsWrapReachesEmptyRange() {
    FixedBitSet parents = new FixedBitSet(4);
    parents.set(3);
    FixedBitSet children = new FixedBitSet(4);
    children.set(0, 3);
    FixedBitSet childDocsWithValue = new FixedBitSet(4); // empty
    Bits docsWithValue = BlockJoinSelector.wrap(childDocsWithValue, parents, children);
    int r =
        probe(
            "BlockJoinSelector.wrap(Bits{},parents{3},children{0,1,2}).get(3) [1=true,0=false]",
            () -> docsWithValue.get(3) ? 1 : 0);
    assertEquals(0, r);
  }

  /** Control for the caller: a child with a value is found before the empty range is reached. */
  public void testControlBlockJoinSelectorBitsWrapEarlyHit() {
    FixedBitSet parents = new FixedBitSet(4);
    parents.set(3);
    FixedBitSet children = new FixedBitSet(4);
    children.set(0, 3);
    FixedBitSet childDocsWithValue = new FixedBitSet(4);
    childDocsWithValue.set(2);
    Bits docsWithValue = BlockJoinSelector.wrap(childDocsWithValue, parents, children);
    int r =
        probe(
            "BlockJoinSelector.wrap(Bits{2},parents{3},children{0,1,2}).get(3) [1=true,0=false]",
            () -> docsWithValue.get(3) ? 1 : 0);
    assertEquals(1, r);
  }
}
