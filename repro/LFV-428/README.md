# DynamicRangeUtil computes DynamicRangeInfo.centroid ('the average value in the range') from an unchecked long sum of the range's values, so when one range's values sum past Long.MAX_VALUE (for example six epoch-nanosecond timestamps of today) it silently returns a centroid outside [min, max], typically negative for positive values

Bug LFV-428 · Area: facet / dynamic range facets (DynamicRangeUtil.computeDynamicRanges and computeDynamicNumericRanges, DynamicRangeInfo.centroid) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index six documents whose long doc-values field holds epoch-nanosecond timestamps one second apart (about 1.76e18 each) and ask DynamicRangeUtil.computeDynamicRanges for one range, or you pass three values of 4e18 to computeDynamicNumericRanges, and expect the range's centroid, documented as 'the average value in the range', to be about 1.7614800025e18 (or exactly 4e18). Instead you get -1.3129773431182584e18 (or -2.148914691236517e18), below the range's own min, with no error, because the method adds the range's values into a long valueSum that silently wraps once the true sum passes Long.MAX_VALUE and then divides the wrapped sum by the count. Count, weight, min and max are correct, and the weight sums in the same class are checked with Math.addExact and throw instead of wrapping.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: You get -1.3129773431182584e18 (or -2.148914691236517e18), below the range's own min, with no error, because the method adds the range's values into a long valueSum that silently wraps once the true sum passes Long.MAX_VALUE and then divides the wrapped sum by the count.

## Files

- [src/org/apache/lucene/facet/range/TestDynamicRangeCentroidOverflow.java](src/org/apache/lucene/facet/range/TestDynamicRangeCentroidOverflow.java) (JUnit test)

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
     execute --select-class org.apache.lucene.facet.range.TestDynamicRangeCentroidOverflow
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/count-stats-modules/](../../models/count-stats-modules/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
