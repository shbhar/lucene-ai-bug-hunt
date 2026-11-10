# Sandbox numeric multi-range doc-values query whose clauses are all reversed (lower &gt; upper) throws NoSuchElementException at search time instead of matching nothing (SortedNumericStabbingBuilder("f").add(3, 1).add(9, 7) on an index where f has a value); since 10.3.0

Finding LFV-1464 · Area: sandbox / doc-values multi-range query (DocValuesMultiRangeQuery.SortedNumericStabbingBuilder -> SortedNumericDocValuesMultiRangeQuery: empty clause set after every reversed clause is dropped) · Priority: low · Impact: crash · Novelty: new

Finding page: [../../bugs/LFV-1464.html](../../bugs/LFV-1464.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index one document whose field f holds 5 (SortedNumericDocValuesField) and count new DocValuesMultiRangeQuery.SortedNumericStabbingBuilder("f").add(3, 1).add(9, 7).build(), a union of two empty ranges (lower &gt; upper); you expect 0 hits, because the query drops reversed ranges on purpose and the same builder returns no hits for no range or for one reversed range. Instead IndexSearcher.count throws java.util.NoSuchElementException: once every clause is dropped the query's clause set is empty (it prints f:[]), and MultiRangeWeight.scorerSupplier reads its first element (sortedClauses.getFirst()) with no empty check.

## Expected vs actual

- Expected: `count` returns 0.
- Actual: `java.util.NoSuchElementException` at `MultiRangeWeight.scorerSupplier` (`SortedNumericDocValuesMultiRangeQuery.java:214`), with assertions on and off.

## Files

- [src/org/apache/lucene/sandbox/search/TestMultiRangeOnlyEmptyClauses.java](src/org/apache/lucene/sandbox/search/TestMultiRangeOnlyEmptyClauses.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.sandbox.search.TestMultiRangeOnlyEmptyClauses
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestMultiRangeOnlyEmptyClauses.java](TestMultiRangeOnlyEmptyClauses.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
