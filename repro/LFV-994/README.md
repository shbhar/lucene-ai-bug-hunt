# SortedSetDocValues facets size the child priority queue from topN instead of the number of labels, so getTopChildren, getAllDims and getTopDims(.., topNChildren) with topN = Integer.MAX_VALUE throw IllegalArgumentException where taxonomy facets return every label, and a large topN allocates topN + 1 queue slots even for a 3-label dim

Bug LFV-994 · Area: facet / SortedSetDocValues child queue (AbstractSortedSetDocValueFacetCounts.computeTopChildren, inherited by SortedSetDocValuesFacetCounts and ConcurrentSortedSetDocValuesFacetCounts) · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a SortedSetDocValues facet dim with three labels and call getTopChildren(Integer.MAX_VALUE, dim) to get all of them, expecting the same result as getTopChildren(1000, dim), because Facets.validateTopN accepts every topN &gt; 0 and taxonomy facets return all three labels for exactly this call. Instead AbstractSortedSetDocValueFacetCounts.computeTopChildren builds new TopOrdAndIntQueue(topN) without capping topN at the number of labels, so PriorityQueue throws IllegalArgumentException 'maxSize must be &gt;= 0 and &lt; 2147483631; got: 2147483647', and getAllDims(Integer.MAX_VALUE) and getTopDims(10, Integer.MAX_VALUE) fail the same way. Below that limit the same line pre-allocates topN + 1 slots: getTopChildren(2^24, dim) over 3 labels allocates about 64 MiB to return 3 results.

The test shows that SortedSetDocValuesFacetCounts sizes the child queue from topN instead of the label count, so getTopChildren(Integer.MAX_VALUE, dim) fails on a 3-label dim.

## Expected vs actual

Expected: all 3 labels, as taxonomy facets return. Actual (-ea and -da): IllegalArgumentException: maxSize must be &gt;= 0 and &lt; 2147483631; got: 2147483647.

## Files

- [src/org/apache/lucene/facet/sortedset/TestSortedSetFacetsTopNMaxValue.java](src/org/apache/lucene/facet/sortedset/TestSortedSetFacetsTopNMaxValue.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.facet.sortedset.TestSortedSetFacetsTopNMaxValue
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/facets-topn-and-topndims-queues-presized-uncapped/](../../models/facets-topn-and-topndims-queues-presized-uncapped/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
