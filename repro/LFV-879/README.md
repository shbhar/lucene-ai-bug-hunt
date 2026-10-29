# DoubleDocValuesStats and SortedDoubleDocValuesStats (lucene/misc) return mean = NaN instead of +/-Infinity when any value is collected after an infinite value, so the mean of {+Infinity, 1.0} is NaN while the mean of {1.0, +Infinity} is +Infinity

Bug LFV-879 · Area: misc / DocValuesStats double statistics (DocValuesStatsCollector, DocValuesStatsCollectorManager) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You collect statistics for a double doc-values field with DocValuesStats.DoubleDocValuesStats (or SortedDoubleDocValuesStats) and expect mean, documented as 'the mean of all values of the field', to be +Infinity for the values {+Infinity, 1.0}: that is sum/count, and it is what java.util.DoubleSummaryStatistics, the oracle of the upstream test, returns. Instead mean is NaN, because the running update mean += (val - mean) / count computes Infinity - Infinity as soon as the running mean is infinite and another value arrives; {+Infinity, +Infinity} also gives NaN, while sum and max report +Infinity. The answer depends on document order ({1.0, +Infinity} gives +Infinity) and, since 10.5.0, on the searcher: with an executor and one segment per slice, DocValuesStatsCollectorManager rebuilds the mean from the sums and returns +Infinity.

The test shows that `DoubleDocValuesStats.mean()` is NaN for a double doc-values field holding +Infinity then 1.0 (the running update computes Infinity - Infinity).

## Expected vs actual

Expected `mean()` = +Infinity (= `sum()` / `count()`); actual NaN (-ea and -da).

## Files

- [src/org/apache/lucene/misc/search/TestDocValuesStatsInfiniteMean.java](src/org/apache/lucene/misc/search/TestDocValuesStatsInfiniteMean.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/misc/build/libs/lucene-misc-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.misc.search.TestDocValuesStatsInfiniteMean
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
