# Sandbox SortedNumericDocValuesMultiRangeQuery on a skip-indexed numeric field judges a document without a value by a later document's values once two value-less documents follow each other in a matching skip block, so it returns extra hits, misses hits or fails with a doc-values read exception (values 1, none, none, 50 with the ranges [40,60] and [100,200] count 2, not 1); since 10.5.0

Finding LFV-1462 · Area: sandbox / doc-values multi-range query (DocValuesMultiRangeQuery.SortedNumericStabbingBuilder -> SortedNumericDocValuesMultiRangeQuery) over the doc-values skip index (core SkipBlockRangeIterator approximation) · Priority: medium · Impact: silent wrong results · Novelty: new

Finding page: [../../bugs/LFV-1462.html](../../bugs/LFV-1462.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index four documents whose field f, added with SortedNumericDocValuesField.indexedField (doc values with a skip index, default codec), holds 1, nothing, nothing and 50, and count the hits of new DocValuesMultiRangeQuery.SortedNumericStabbingBuilder("f").add(40, 60).add(100, 200).build(); you expect 1, the document with 50, since the query is documented as the union of its ranges, and that is what the same query gives without a skip index or as a BooleanQuery of the two core range queries. Instead the count is 2: with a skip index the query visits every document of a matching block, matches() on the first value-less document moves the value cursor on to the document with 50 and returns false, and matches() on the second value-less document finds the cursor already past it, skips the presence check and judges it by the value 50. On multi-valued fields that later document's values are then used up, so it is judged by the following document's values, or the read runs past the last value and throws.

## Expected vs actual

- Expected: count 1: only the document with 50 has a value in a range.
- Actual: count 2: `AssertionError: expected:<1> but was:<2>` at line 50, with assertions on and off.

## Files

- [src/org/apache/lucene/sandbox/search/TestMultiRangeSparseSkipIndex.java](src/org/apache/lucene/sandbox/search/TestMultiRangeSparseSkipIndex.java) (JUnit test)

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
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.sandbox.search.TestMultiRangeSparseSkipIndex
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestMultiRangeSparseSkipIndex.java](TestMultiRangeSparseSkipIndex.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
