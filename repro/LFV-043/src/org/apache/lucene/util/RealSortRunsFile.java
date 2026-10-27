package org.apache.lucene.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** Usage: RealSortRunsFile runs.txt -- builds the 0/1 list and calls CollectionUtil.timSort. */
public class RealSortRunsFile {
  public static void main(String[] args) throws Exception {
    int[] runs = Files.readAllLines(Path.of(args[0])).stream().filter(s -> !s.isBlank()).mapToInt(Integer::parseInt).toArray();
    long n = TestTimSorterStackOverflow.sum(runs);
    int mr = TimSorter.minRun((int) n);
    int min = Integer.MAX_VALUE;
    for (int i = 0; i < runs.length - 1; i++) min = Math.min(min, runs[i]);
    System.out.println(args[0] + ": n=" + n + " runs=" + runs.length + " minRun(n)=" + mr + " min run (excl. last)=" + min + " legal=" + (min >= mr && n <= Integer.MAX_VALUE));
    TestTimSorterStackOverflow.BitList list = new TestTimSorterStackOverflow.BitList(TestTimSorterStackOverflow.encode(runs, false), (int) n);
    try {
      CollectionUtil.timSort(list, Comparator.<Boolean>naturalOrder());
      System.out.println("  CollectionUtil.timSort completed");
    } catch (ArrayIndexOutOfBoundsException e) {
      System.out.println("  CollectionUtil.timSort threw " + e + " at " + e.getStackTrace()[0]);
    }
  }
}
