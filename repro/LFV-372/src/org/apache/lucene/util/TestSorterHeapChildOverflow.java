package org.apache.lucene.util;

import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Sorter.heapChild(from, i) = ((i - from) << 1) + 1 + from is computed in int. When the real child
 * index is >= 2^31 (no child exists), it wraps to a negative number, which passes the
 * {@code leftChild < to} check in siftDown, so heapSort compares a slot outside [from, to).
 *
 * <p>Uses the package-private Sorter#heapSort (the fallback IntroSorter takes once its depth
 * budget is spent), from a test in the same package. Slots are plain ints; the sorter maps slot
 * s to data[s - base], so a 1,000-slot range that ends at Integer.MAX_VALUE needs no large array.
 * The same arithmetic applies to any heap range with 2 * to - from > 2^31, e.g. from = 0 and
 * to > 2^30.
 */
public class TestSorterHeapChildOverflow extends LuceneTestCase {

  /** An IntroSorter over int values held for slots [base, base + data.length). */
  private static final class OffsetIntSorter extends IntroSorter {
    final int[] data;
    final int base;
    String outOfRange;
    int pivot;

    OffsetIntSorter(int[] data, int base) {
      this.data = data;
      this.base = base;
    }

    private int idx(int slot) {
      if (slot < base || slot - base >= data.length) {
        if (outOfRange == null) {
          outOfRange = "slot " + slot + " outside [" + base + ", " + (base + data.length) + ")";
        }
        throw new IndexOutOfBoundsException(outOfRange);
      }
      return slot - base;
    }

    @Override
    protected void swap(int i, int j) {
      int a = idx(i), b = idx(j);
      int t = data[a];
      data[a] = data[b];
      data[b] = t;
    }

    @Override
    protected int compare(int i, int j) {
      return Integer.compare(data[idx(i)], data[idx(j)]);
    }

    @Override
    protected void setPivot(int i) {
      pivot = data[idx(i)];
    }

    @Override
    protected int comparePivot(int j) {
      return Integer.compare(pivot, data[idx(j)]);
    }
  }

  private static void heapSortAndCheck(int base, int n) {
    int[] data = new int[n];
    for (int i = 0; i < n; i++) {
      data[i] = i; // ascending: every parent is smaller than its children, so siftDown descends
    }
    OffsetIntSorter sorter = new OffsetIntSorter(data, base);
    try {
      sorter.heapSort(base, base + n);
    } catch (IndexOutOfBoundsException e) {
      fail("heapSort(" + base + ", " + (base + n) + ") accessed " + sorter.outOfRange);
    }
    assertNull(sorter.outOfRange);
    for (int i = 1; i < n; i++) {
      assertTrue(data[i - 1] <= data[i]);
    }
  }

  /** Control: the same 1,000-slot heap sort works at the start of the int range. */
  public void testSmallSlotsControl() {
    heapSortAndCheck(0, 1000);
  }

  /** A 1,000-slot range that ends at Integer.MAX_VALUE: 2 * to - from > 2^31. */
  public void testRangeEndingAtIntMax() {
    heapSortAndCheck(Integer.MAX_VALUE - 1000, 1000);
  }
}
