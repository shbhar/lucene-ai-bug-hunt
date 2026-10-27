# DoubleDocValuesStats and SortedDoubleDocValuesStats (lucene/misc) start max at Double.MIN_VALUE, the smallest positive double, so max returns 4.9E-324 instead of the real maximum whenever every collected value is &lt;= 0 (and min returns Double.MAX_VALUE when every value is +Infinity)

Bug LFV-155 · Area: misc / DocValuesStats double statistics (DocValuesStatsCollector, DocValuesStatsCollectorManager) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-155.html](../../bugs/LFV-155.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You collect statistics for a double doc-values field with DocValuesStats.DoubleDocValuesStats (or SortedDoubleDocValuesStats) and expect max to be the largest collected value; the javadoc calls it 'the maximum value of the field' and leaves it undefined only when count is zero. If every collected value is zero or negative, max returns 4.9E-324 instead: max starts at Double.MIN_VALUE, which is the smallest positive double, not the most negative one, and no value &lt;= 0 ever beats it, so for {-5, -3, -4} you get count 3 and min -5.0 but max 4.9E-324. The same mix-up on the other side makes min return Double.MAX_VALUE when every value is +Infinity; the long variants start at Long.MAX_VALUE / Long.MIN_VALUE and are correct.

## Expected vs actual

- Expected: max of the winter readings {-12.5, -3.0, -7.25} is -3.0, of {-2.0} is -2.0 and of four 0.0 values is 0.0.
- Actual: max is 4.9E-324 in all three, while count, sum, mean and min are right; min of two +Infinity values is 1.7976931348623157E308.

## Files

- [src/org/apache/lucene/misc/search/TestDocValuesStatsDoubleSentinels.java](src/org/apache/lucene/misc/search/TestDocValuesStatsDoubleSentinels.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/misc/build/libs/lucene-misc-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.misc.search.TestDocValuesStatsDoubleSentinels
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestDocValuesStatsDoubleExtremes.java](TestDocValuesStatsDoubleExtremes.java)
- [TestDocValuesStatsDoubleSentinels.java](TestDocValuesStatsDoubleSentinels.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
