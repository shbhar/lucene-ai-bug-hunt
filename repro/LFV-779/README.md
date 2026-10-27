# With an INT index sort, merges order documents by the full 64-bit doc value while flush, sorted search and CheckIndex use its low 32 bits, so merging a value outside the int range writes a segment that violates its declared index sort (CheckIndex at level 2 or higher rejects it) and sorted searches that terminate early on the index sort return wrong top hits (a regression in 8.2.0)

Bug LFV-779 · Area: index / index sorting at merge time (IndexSorter.IntSorter merge keys, MultiSorter) -> search / TopFieldCollector index-sort early termination · Priority: medium · Impact: data corruption · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You configure an index sort on SortField("s", SortField.Type.INT), index documents with NumericDocValuesField("s", value), one of them with a value that does not fit in an int (for example 2^32, whose int value is 0), and let segments merge, expecting the merged segment to be sorted the way a flushed segment of the same documents is. Flush (IntSorter#getDocComparator), the search-time IntComparator and CheckIndex all compare (int) of the value, but the merge key (IntSorter#getComparableProviders) is the full long, so forceMerge(1) of the segments [a = 2^32] and [b = 1, c = 2] writes the order b, c, a instead of a, b, c. CheckIndex (level 2 or higher) then reports 'segment has indexSort=&lt;int: "s"&gt; but docID=1 sorts after docID=2' and a top-1 search sorted by the index sort returns b instead of a, while before the merge the same index is clean and returns a.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/index/TestIntIndexSortMergeOrder.java](src/org/apache/lucene/index/TestIntIndexSortMergeOrder.java) (JUnit test)
- [src2/org/apache/lucene/index/TestIntIndexSortMergeOrder.java](src2/org/apache/lucene/index/TestIntIndexSortMergeOrder.java) (JUnit test)
- [src3/org/apache/lucene/index/TestIntIndexSortMergeOrder.java](src3/org/apache/lucene/index/TestIntIndexSortMergeOrder.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')
javac -d classes-src3 -cp "$CP" $(find src3 -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestIntIndexSortMergeOrder
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestIntIndexSortMergeOrder
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src3:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestIntIndexSortMergeOrder
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
