# SortedDoubleDocValuesStats (lucene/misc) reads core DoubleField values as raw IEEE bits, so negative values give a silently wrong min, sum and mean (DoubleField -5.0 and 3.0 give min -0.8749999999999999)

Finding LFV-1129 · Area: misc / DocValuesStats: SortedDoubleDocValuesStats decode of sorted-numeric double doc values (core DoubleField) · Priority: medium · Impact: silent wrong results · Novelty: new

Finding page: [../../bugs/LFV-1129.html](../../bugs/LFV-1129.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index two documents with DoubleField("f", -5.0) and DoubleField("f", 3.0) and search MatchAllDocsQuery with new DocValuesStatsCollectorManager&lt;&gt;(() -&gt; new SortedDoubleDocValuesStats("f")); you expect min() = -5.0 (sum -2.0, mean -1.0), because the class 'Holds DocValues statistics for a sorted-numeric field storing double values' and DoubleField is core's sorted-numeric double field. You get min() = -0.8749999999999999 (sum 2.125, mean 1.0625) with no error: DoubleField stores NumericUtils.doubleToSortableLong(value), but SortedDoubleDocValuesStats decodes each stored long as raw IEEE bits (Double.longBitsToDouble, DocValuesStats.java:436). Positive values are unaffected because their sortable and raw bits are the same.

## Expected vs actual

- Expected: `min()` is -5.0 (and sum -2.0, mean -1.0).
- Actual: `AssertionError: expected:<-5.0> but was:<-0.8749999999999999>`, no other error, with assertions on and off.

## Files

- [src/org/apache/lucene/misc/search/TestSortedDoubleStatsDoubleField.java](src/org/apache/lucene/misc/search/TestSortedDoubleStatsDoubleField.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/misc/build/libs/lucene-misc-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.misc.search.TestSortedDoubleStatsDoubleField
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestSortedDoubleStatsDoubleField.java](TestSortedDoubleStatsDoubleField.java)

Formal models and scripts this finding's part of the hunt used: [../../models/docvaluesstats-sorted-double-stats-decode-sortable-doubles-as-raw-bits/](../../models/docvaluesstats-sorted-double-stats-decode-sortable-doubles-as-raw-bits/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
