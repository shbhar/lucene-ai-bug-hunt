# DocValuesStatsCollectorManager merges LongDocValuesStats and SortedLongDocValuesStats (lucene/misc) through their wrapping long sum, so on a searcher with an executor mean, variance and stdev are silently wrong (for example about 8x too small, or negative) once a slice's long sum or the reduce's running long sum passes Long.MAX_VALUE, as 6 epoch-nanosecond timestamps do, while the same search without an executor is exact

Bug LFV-882 · Area: misc / DocValuesStats long statistics (DocValuesStatsCollectorManager concurrent reduce) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-882.html](../../bugs/LFV-882.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index ordinary large long values, for example 24 epoch-nanosecond timestamps in 6 segments, compute LongDocValuesStats (or SortedLongDocValuesStats) with DocValuesStatsCollectorManager exactly as its class javadoc shows on new IndexSearcher(reader, executor), and expect mean = 1.7616096115E18, 'the mean of all values of the field' and the value the same search without an executor returns, because merge promises 'the combined statistics of both segments as if they had been collected together'. Instead mean is 2.243809386908707E17 (about 8x too small; other slicings give a negative mean) and variance and stdev are wrong too, because merge recomputes the combined mean as (sum + otherSum) / count from long sums that have wrapped past Long.MAX_VALUE, while serial collection keeps a running mean that never reads the sum. Only sum is documented as possibly overflowing; count, min and max stay right.

The test shows that `DocValuesStatsCollectorManager` with `LongDocValuesStats` on an `IndexSearcher` with an executor returns a wrong `mean()` once a slice's long sum wraps (6 one-doc segments of the value 2e18, default slicing 5 + 1).

## Expected vs actual

Expected the executor mean to equal the serial mean `2.0E18`; actual: `-1.0744573456182586E18`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/misc/search/TestDocValuesStatsMergedMean.java](src/org/apache/lucene/misc/search/TestDocValuesStatsMergedMean.java) (JUnit test)

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
     execute --select-class org.apache.lucene.misc.search.TestDocValuesStatsMergedMean
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
