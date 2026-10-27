package org.apache.lucene.util;

import java.util.Comparator;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Run ONLY with fix-experiment/out (a TimSorter patched with the JDK-8203864 check) placed before
 * lucene-core on the classpath. Checks that the inputs that overflow main sort correctly then.
 */
public class TestTimSorterFixExperiment extends LuceneTestCase {

  private static void check(int[] runs, boolean lastRunHasOne, String name) {
    int n = (int) TestTimSorterStackOverflow.sum(runs);
    FixedBitSet bits = TestTimSorterStackOverflow.encode(runs, lastRunHasOne);
    int ones = (int) bits.cardinality();
    TestTimSorterStackOverflow.BitTimSorter sorter =
        new TestTimSorterStackOverflow.BitTimSorter(bits, n / 64);
    sorter.sort(0, n);
    assertEquals(ones, bits.cardinality());
    assertEquals(n - ones, bits.nextSetBit(0));
    System.out.println(name + ": patched TimSorter.sort OK, max stackSize seen " + sorter.maxStackSeen);
  }

  public void testPatchedSorterHandlesLegalOverflowInput() {
    check(TestTimSorterStackOverflow.LEGAL_RUNS, false, "legal array");
    int n = (int) TestTimSorterStackOverflow.sum(TestTimSorterStackOverflow.LEGAL_RUNS);
    TestTimSorterStackOverflow.BitList list =
        new TestTimSorterStackOverflow.BitList(
            TestTimSorterStackOverflow.encode(TestTimSorterStackOverflow.LEGAL_RUNS, false), n);
    CollectionUtil.timSort(list, Comparator.<Boolean>naturalOrder());
    for (int i = 1; i < n; i += 1 + (i >>> 4)) assertTrue(list.get(i - 1).compareTo(list.get(i)) <= 0);
    System.out.println("legal list: patched CollectionUtil.timSort OK");
  }

  public void testPatchedSorterHandlesPublishedInput() {
    check(TestTimSorterStackOverflow.PUBLISHED_RUNS, true, "published array");
  }
}
