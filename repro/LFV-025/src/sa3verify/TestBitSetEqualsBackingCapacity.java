package sa3verify;

import java.util.HashSet;
import java.util.Set;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.FixedBitSet;
import org.apache.lucene.util.LongBitSet;

/**
 * Independent verification of the finding: FixedBitSet/LongBitSet equals() also compares the
 * length of the backing long[] (ghost words), so two sets with the same numBits and the same set
 * bits but different backing-array capacity compare unequal.
 *
 * <p>Fully deterministic: no use of random(). Tests named test*Contract assert the behaviour the
 * javadoc promises ("same bits set") and are EXPECTED TO FAIL on main if the candidate is real.
 * Tests named testControl* are sanity checks that must pass.
 */
public class TestBitSetEqualsBackingCapacity extends LuceneTestCase {

  private static String describe(FixedBitSet a, FixedBitSet b) {
    return "length=" + a.length() + "/" + b.length()
        + " cardinality=" + a.cardinality() + "/" + b.cardinality()
        + " hashCode=" + a.hashCode() + "/" + b.hashCode()
        + " backingWords=" + a.getBits().length + "/" + b.getBits().length
        + " equals=" + a.equals(b) + "/" + b.equals(a);
  }

  private static String describe(LongBitSet a, LongBitSet b) {
    return "length=" + a.length() + "/" + b.length()
        + " cardinality=" + a.cardinality() + "/" + b.cardinality()
        + " hashCode=" + a.hashCode() + "/" + b.hashCode()
        + " backingWords=" + a.getBits().length + "/" + b.getBits().length
        + " equals=" + a.equals(b) + "/" + b.equals(a);
  }

  /** Checks bit-for-bit identity of the logical content (0..length-1). */
  private static void assertSameLogicalBits(FixedBitSet a, FixedBitSet b) {
    assertEquals(a.length(), b.length());
    for (int i = 0; i < a.length(); i++) {
      assertEquals("bit " + i, a.get(i), b.get(i));
    }
    assertEquals(a.cardinality(), b.cardinality());
  }

  private static void assertSameLogicalBits(LongBitSet a, LongBitSet b) {
    assertEquals(a.length(), b.length());
    for (long i = 0; i < a.length(); i++) {
      assertEquals("bit " + i, a.get(i), b.get(i));
    }
    assertEquals(a.cardinality(), b.cardinality());
  }

  // ---------------------------------------------------------------- FixedBitSet

  /** numBits = 100 (2 words). One set uses an exact long[2], the other a long[5]. */
  public void testFixedBitSetContract() {
    FixedBitSet exact = new FixedBitSet(100);
    FixedBitSet roomy = new FixedBitSet(new long[5], 100); // documented as allowed
    for (int bit : new int[] {0, 7, 63, 64, 99}) {
      exact.set(bit);
      roomy.set(bit);
    }
    assertSameLogicalBits(exact, roomy);
    assertEquals(exact.hashCode(), roomy.hashCode());
    System.out.println("FixedBitSet  exact vs roomy: " + describe(exact, roomy));
    assertTrue("FixedBitSet equals: " + describe(exact, roomy), exact.equals(roomy));
    assertTrue("FixedBitSet equals (sym): " + describe(exact, roomy), roomy.equals(exact));
  }

  /** Practical consequence: hash-based collections treat the two as different keys. */
  public void testFixedBitSetHashSetContract() {
    FixedBitSet exact = new FixedBitSet(10);
    exact.set(2);
    FixedBitSet roomy = new FixedBitSet(new long[2], 10);
    roomy.set(2);
    Set<FixedBitSet> set = new HashSet<>();
    set.add(exact);
    System.out.println("FixedBitSet  HashSet.contains(roomy) = " + set.contains(roomy));
    assertTrue("HashSet lookup with an equal-content FixedBitSet", set.contains(roomy));
  }

  /** Empty sets: every bit is clear in both, still unequal if capacity differs. */
  public void testFixedBitSetEmptyContract() {
    FixedBitSet exact = new FixedBitSet(64);
    FixedBitSet roomy = new FixedBitSet(new long[2], 64);
    System.out.println("FixedBitSet  empty exact vs roomy: " + describe(exact, roomy));
    assertTrue("empty FixedBitSets: " + describe(exact, roomy), exact.equals(roomy));
  }

  // ---------------------------------------------------------------- LongBitSet

  public void testLongBitSetContract() {
    LongBitSet exact = new LongBitSet(100);
    LongBitSet roomy = new LongBitSet(new long[5], 100);
    for (long bit : new long[] {0, 7, 63, 64, 99}) {
      exact.set(bit);
      roomy.set(bit);
    }
    assertSameLogicalBits(exact, roomy);
    assertEquals(exact.hashCode(), roomy.hashCode());
    System.out.println("LongBitSet   exact vs roomy: " + describe(exact, roomy));
    // LongBitSet.equals javadoc: "returns true if both sets have the same bits set"
    assertTrue("LongBitSet equals: " + describe(exact, roomy), exact.equals(roomy));
    assertTrue("LongBitSet equals (sym): " + describe(exact, roomy), roomy.equals(exact));
  }

  // ---------------------------------------------------------------- controls (must pass)

  /** Same backing capacity (both oversized to long[5]) -> equal. Isolates capacity as the cause. */
  public void testControlSameCapacityEqual() {
    FixedBitSet r1 = new FixedBitSet(new long[5], 100);
    FixedBitSet r2 = new FixedBitSet(new long[5], 100);
    LongBitSet l1 = new LongBitSet(new long[5], 100);
    LongBitSet l2 = new LongBitSet(new long[5], 100);
    for (int bit : new int[] {0, 7, 63, 64, 99}) {
      r1.set(bit);
      r2.set(bit);
      l1.set(bit);
      l2.set(bit);
    }
    assertTrue(describe(r1, r2), r1.equals(r2));
    assertTrue(describe(l1, l2), l1.equals(l2));
    // exact vs exact also equal
    FixedBitSet e1 = new FixedBitSet(100);
    FixedBitSet e2 = new FixedBitSet(100);
    e1.set(5);
    e2.set(5);
    assertTrue(e1.equals(e2));
  }

  /** clone() keeps the oversized capacity, so clone(roomy) == roomy, but != a fresh exact copy. */
  public void testControlCloneKeepsCapacity() {
    FixedBitSet roomy = new FixedBitSet(new long[5], 100);
    roomy.set(42);
    FixedBitSet cl = roomy.clone();
    assertEquals(5, cl.getBits().length);
    assertTrue(roomy.equals(cl));
    FixedBitSet exact = new FixedBitSet(100);
    exact.or(roomy); // logically identical content
    System.out.println("FixedBitSet  clone(roomy) vs exact.or(roomy): " + describe(cl, exact));
    // record (do not assert) the asymmetric observation for the README
  }

  /** A real bit difference is still detected (equals is not broken in the other direction). */
  public void testControlDifferentBitsUnequal() {
    FixedBitSet a = new FixedBitSet(100);
    FixedBitSet b = new FixedBitSet(new long[5], 100);
    a.set(1);
    b.set(2);
    assertFalse(a.equals(b));
    FixedBitSet c = new FixedBitSet(100);
    FixedBitSet d = new FixedBitSet(101); // different numBits -> unequal by design
    assertFalse(c.equals(d));
  }
}
