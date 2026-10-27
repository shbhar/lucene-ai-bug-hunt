package repro;

import java.util.Arrays;
import java.util.Collections;
import org.apache.lucene.util.RamUsageEstimator;

/** Version-neutral main() form of TestRamUsageEstimatorCollectionSizeOverflow (exit 1 = bug present). */
public class RamUsageCollectionOverflowMain {
  public static void main(String[] args) {
    int n = (Integer.MAX_VALUE - RamUsageEstimator.NUM_BYTES_ARRAY_HEADER) / RamUsageEstimator.NUM_BYTES_OBJECT_REF + 1;
    Object[] backing = new Object[n];
    long a = RamUsageEstimator.sizeOfCollection(Arrays.asList(backing));
    long b = RamUsageEstimator.sizeOfCollection(Collections.nCopies(n, null));
    long arr = RamUsageEstimator.shallowSizeOf(backing);
    System.out.println("n=" + n + " sizeOfCollection(Arrays.asList)=" + a + " sizeOfCollection(nCopies)=" + b
        + " shallowSizeOf(backing Object[])=" + arr);
    if (a < arr || b < 0) {
      System.out.println("BUG: estimate below the backing array size / negative");
      System.exit(1);
    }
  }
}
