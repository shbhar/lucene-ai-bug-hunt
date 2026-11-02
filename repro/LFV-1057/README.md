# Since #16857 (upstream main and branch_11x, unreleased), a numeric field-sorted search reads a later segment's whole competitive points range before the scorer is set: TopFieldLeafCollector's constructor calls setHitsThresholdReached() while NumericComparator's iteratorCost is still its -1 'no scorer yet' value, so the 8x selectivity check never rejects (1,001 point doc IDs read where releases read 0; performance regression, results correct)

Finding LFV-1057 · Area: search / field sorting: TopFieldCollector + NumericComparator points-based sort pruning (competitive iterator) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1057.html](../../bugs/LFV-1057.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run searcher.search(new TermQuery(new Term("brand", "lux")), 10, new Sort(new SortField("price", SortField.Type.LONG))) over two segments: the first has 1,001 hits, so the default 1,000-hit threshold is crossed there, and in the second the query matches one document that cannot compete. You expect the second segment not to read its points, because the comparator builds its competitive iterator from points only when that cuts the query's own cost at least 8x; 645d4fe, 10.6.0, 10.5.2 and 9.12.3 read 0 point doc IDs there. Instead, since #16857 the new leaf collector tells the comparator about the threshold before setScorer(), while its cost is still the -1 'no scorer yet' value (so the 8x limit becomes 2^61-1), and it intersects the whole competitive range, 1,001 doc IDs; the top 10 is still right, only the work is wasted.

## Expected vs actual

- Expected: 0 point doc IDs read in segment 1, where the query matches 1 document, as on 10.6.0, 10.5.2, 9.12.3 and main before #16857.
- Actual: `AssertionError: expected:<0> but was:<1001>` on main `716d6f216f5`, with assertions on and off; the search result itself is unchanged.

## Files

- [src/org/apache/lucene/search/TestSortLaterSegmentPointsWork.java](src/org/apache/lucene/search/TestSortLaterSegmentPointsWork.java) (JUnit test)

## Run it

- Tested on Lucene main 716d6f216f52 (2026-10-29): reproduced, with assertions on and off.
- Tested on Lucene branch_11x 603af8703753 (2026-10-29): reproduced, with assertions on and off (the core sources that differ from main 645d4fe14a20, taken from this commit and compiled over the 645d4fe14a20 jars).
- Main 645d4fe14a20 passes: #16857 came after it. A branch_11x checkout builds 11.0.0-SNAPSHOT jars, so set V=11.0.0-SNAPSHOT there.
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
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestSortLaterSegmentPointsWork
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestSortLaterSegmentPointsWork.java](TestSortLaterSegmentPointsWork.java)

Formal models and scripts this finding's part of the hunt used: [../../models/sort-leafstart-hitsthreshold-before-setscorer-points-intersect-16857/](../../models/sort-leafstart-hitsthreshold-before-setscorer-points-intersect-16857/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
