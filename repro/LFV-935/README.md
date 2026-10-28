# QualityStats.average(stats).getPrecisionAt(n) is not the mean per-query precision at n when n is above the mean result count: the mean that average stored is replaced by a second extrapolation (mean P@10 0.4 instead of 0.6)

Bug LFV-935 · Area: benchmark / search-quality evaluation (QualityStats) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You evaluate search quality with QualityBenchmark, average the per-query results with QualityStats.average(stats) and ask the average for its precision at 10, expecting the mean of the per-query P@10 values (which, like trec_eval's P_10, already count missing ranks as non-relevant). If the queries returned different numbers of results and their mean is below 10, getPrecisionAt(10) ignores the correct mean that average stored and extrapolates again from the precision at the mean result count: with setMaxResults(10), a query whose 10 hits are all relevant (P@10 = 1.0) and a query whose only 2 hits are relevant (P@10 = 0.2) give 0.4 instead of 0.6. There is no error, and the printed SUMMARY never shows the affected cutoffs.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/benchmark/quality/TestQualityStatsAveragePrecisionAt.java](src/org/apache/lucene/benchmark/quality/TestQualityStatsAveragePrecisionAt.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/benchmark/build/libs/lucene-benchmark-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.benchmark.quality.TestQualityStatsAveragePrecisionAt
```

The module(s) benchmark also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
