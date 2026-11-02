# Since #16857 (upstream main and branch_11x, unreleased), a segment that its index sort terminates early can still build an unused points-based competitive iterator when an earlier segment of the same field-sorted search crossed the hits threshold without early termination: NumericComparator keeps hitsThresholdReached on the comparator that all leaves share, so IndexSortedLeafFieldComparator's not forwarding setHitsThresholdReached() no longer stops the delegate's BKD work (512 point doc IDs read in the repro, up to about cost/7 per segment, where 10.6.0 reads 0; performance regression, results correct)

Finding LFV-1088 · Area: search / field sorting: index-sort early termination (IndexSortedLeafFieldComparator) + NumericComparator points-based sort pruning (competitive iterator) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1088.html](../../bugs/LFV-1088.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You search a MultiReader over two indexes for the top 10 by a long field f: the first index has no index sort and 2,000 hits, so the default 1,000-hit threshold is crossed there, and the second (4,096 documents) is sorted by f, so the search stops in it after a few documents. You expect that sorted segment to read no points: the new IndexSortedLeafFieldComparator says the comparator's skipping work, 'e.g. building iterators from points, is redundant work' there, and 10.6.0 reads 0. Instead, since #16857 the comparator still sees the threshold flag that the first segment set on the comparator shared by all segments, so it walks the sorted segment's BKD tree and builds a points iterator that nobody uses (512 point doc IDs here, up to about cost/7 per segment); the top 10 is still right, only the work is wasted.

## Expected vs actual

- Expected: 0 point-tree opens in the index-sorted segment, as on 10.6.0 and on main before #16857.
- Actual: `AssertionError: expected:<0> but was:<2>` on main `e1faeba08bde`, assertions on and off; the top 10 is unchanged.

## Files

- [src/org/apache/lucene/search/TestIndexSortedSegmentPointsWork.java](src/org/apache/lucene/search/TestIndexSortedSegmentPointsWork.java) (JUnit test)

## Run it

- Tested on Lucene main e1faeba08bde (2026-11-02): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx2g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestIndexSortedSegmentPointsWork
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestIndexSortedSegmentPointsWork.java](TestIndexSortedSegmentPointsWork.java)

Formal models and scripts this finding's part of the hunt used: [../../models/indexsorted-leaf-delegate-points-work-after-mixed-leaf/](../../models/indexsorted-leaf-delegate-points-work-after-mixed-leaf/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
