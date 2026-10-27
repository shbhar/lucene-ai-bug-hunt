# Range facet counters add every hit the fastMatchQuery rejects to FacetResult.value, so the dimension total is too high and depends on the segment layout

Bug LFV-151 · Area: facet / range and range-on-range facet counts (FacetResult.value with a fastMatchQuery) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-151.html](../../bugs/LFV-151.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a range facet counter (LongRangeFacetCounts or DoubleRangeFacetCounts on a single-valued field or values source, or a range-on-range counter) with a fastMatchQuery that removes only hits outside every range, which is what it is for, and read FacetResult.value. You expect the same dimension total as without the fastMatchQuery: the number of hits that fall into at least one range. Instead each segment adds all of its hits up front but subtracts only the documents the fastMatchQuery let through, so the hits it rejected stay in the total (3 instead of 1 in the minimal repro, while the range count is 1), and the same documents and query give 3 in one segment and 1 in two.

## Expected vs actual

- Expected: `FacetResult.value` is 1 for every counter, with or without the `fastMatchQuery`, in one segment or two.
- Actual: With the `fastMatchQuery` in one segment the totals are `[3, 3, 3, 3, 3, 3, 1]` (only the multi-valued control is right), in two segments 1; every range count is 1.

## Files

- [src/org/apache/lucene/facet/rangeonrange/TestRangeOnRangeFastMatchTotalCount.java](src/org/apache/lucene/facet/rangeonrange/TestRangeOnRangeFastMatchTotalCount.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
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
     execute --select-class org.apache.lucene.facet.rangeonrange.TestRangeOnRangeFastMatchTotalCount
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestRangeFacetFastMatchTotal.java](TestRangeFacetFastMatchTotal.java)
- [TestRangeOnRangeFastMatchTotalCount.java](TestRangeOnRangeFastMatchTotalCount.java)
- [TestRangeFacetTotalFastMatchV1.java](TestRangeFacetTotalFastMatchV1.java)
- [TestRangeFacetTotalSegmentDependence.java](TestRangeFacetTotalSegmentDependence.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
