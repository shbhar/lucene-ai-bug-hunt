# Sandbox HistogramCollectorManager fails the whole search with ArithmeticException when one segment lists a skip-indexed field but no document in it has a value (an empty DocValuesSkipper), because the dense leaf collector's '&lt;= 1024' bucket-span check passes for the empty skipper's inverted bounds (10.2.0 through 10.6.0 and main)

Finding LFV-1472 · Area: sandbox / facets: plain histograms (HistogramCollector's skip-index dense leaf collector, single-valued NUMERIC or SORTED_NUMERIC field) · Priority: low · Impact: crash · Novelty: new

Finding page: [../../bugs/LFV-1472.html](../../bugs/LFV-1472.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index two documents with the default codec, one without the field and one with NumericDocValuesField.indexedField("f", 50) (a doc-values skip index, as HistogramCollectorManager's javadoc recommends), delete the second, call forceMerge(1), and run IndexSearcher.search(MatchAllDocsQuery.INSTANCE, new HistogramCollectorManager("f", 10)). You expect an empty histogram, which is what the same index returns without a skip index and what the collector does for a segment that lacks the field. Instead the search throws ArithmeticException 'integer overflow': the merged segment still lists f, its skipper is empty (docCount() 0, minValue() Long.MAX_VALUE, maxValue() Long.MIN_VALUE, a state core writes and CheckIndex accepts), so the bucket span floorDiv(max, 10) - floorDiv(min, 10) is negative, passes the one-sided '&lt;= 1024' check in HistogramCollector.getLeafCollector, and the dense HistogramLeafCollector's new int[Math.toIntExact(span + 1)] throws.

## Expected vs actual

- Expected: An empty histogram (`size()` 0), as the same index gives without a skip index.
- Actual: `ArithmeticException: integer overflow` at `HistogramCollector$HistogramLeafCollector.<init>(HistogramCollector.java:274)`, with assertions on and off.

## Files

- [src/org/apache/lucene/sandbox/facet/plain/histograms/TestHistogramEmptySkipIndex.java](src/org/apache/lucene/sandbox/facet/plain/histograms/TestHistogramEmptySkipIndex.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:$LUCENE/lucene/sandbox/build/libs/lucene-sandbox-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.sandbox.facet.plain.histograms.TestHistogramEmptySkipIndex
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestHistogramEmptySkipIndex.java](TestHistogramEmptySkipIndex.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
