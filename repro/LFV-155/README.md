# DoubleDocValuesStats and SortedDoubleDocValuesStats (lucene/misc) start max at Double.MIN_VALUE, the smallest positive double, so max returns 4.9E-324 instead of the real maximum whenever every collected value is &lt;= 0 (and min returns Double.MAX_VALUE when every value is +Infinity)

Bug LFV-155 · Area: misc / DocValuesStats double statistics (DocValuesStatsCollector, DocValuesStatsCollectorManager) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-155.html](../../bugs/LFV-155.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You collect statistics for a double doc-values field with DocValuesStats.DoubleDocValuesStats (or SortedDoubleDocValuesStats) and expect max to be the largest collected value; the javadoc calls it 'the maximum value of the field' and leaves it undefined only when count is zero. If every collected value is zero or negative, max returns 4.9E-324 instead: max starts at Double.MIN_VALUE, which is the smallest positive double, not the most negative one, and no value &lt;= 0 ever beats it, so for {-5, -3, -4} you get count 3 and min -5.0 but max 4.9E-324. The same mix-up on the other side makes min return Double.MAX_VALUE when every value is +Infinity; the long variants start at Long.MAX_VALUE / Long.MIN_VALUE and are correct.

The test shows that DocValuesStats.DoubleDocValuesStats starts max at Double.MIN_VALUE (the smallest positive double), so max() is wrong when every collected value is &lt;= 0. Here there is one document with -1.0.

## Expected vs actual

Expected: max() == -1.0. Actual (-ea and -da): 4.9E-324.

## Files

- [src/org/apache/lucene/misc/search/TestDoubleDocValuesStatsNegativeMax.java](src/org/apache/lucene/misc/search/TestDoubleDocValuesStatsNegativeMax.java) (JUnit test)

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
     execute --select-class org.apache.lucene.misc.search.TestDoubleDocValuesStatsNegativeMax
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
