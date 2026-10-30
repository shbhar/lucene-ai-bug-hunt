# IndexSortSortedNumericDocValuesRangeQuery casts its long bounds to int on an INT index sort, so Long.MIN_VALUE / Long.MAX_VALUE open ends (or any bound outside the int range) silently return wrong hits and counts

Bug LFV-1049 · Area: search / IndexSortSortedNumericDocValuesRangeQuery (index-sort range shortcut: INT sort comparator path and 4-byte points path) · Priority: medium · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You sort the index by SortField("f", SortField.Type.INT), index the values -5, 3 and 20 in a NumericDocValuesField, and search new IndexSortSortedNumericDocValuesRangeQuery("f", Long.MIN_VALUE, 10, fallback), where the fallback is NumericDocValuesField.newSlowRangeQuery("f", Long.MIN_VALUE, 10) (f &lt;= 10, written with the open-end convention that the fallback documents). You expect the 2 documents the fallback returns, as the class javadoc requires. You get 1: the doc-values binary search narrows the bound with (int) Long.MIN_VALUE, which is 0, so -5 is missed; (int) Long.MAX_VALUE is -1 in the same way, and the 4-byte points path has the same cast.

## Expected vs actual

- Expected: You expect the 2 documents the fallback returns, as the class javadoc requires.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/search/TestIndexSortIntSortLongBound.java](src/org/apache/lucene/search/TestIndexSortIntSortLongBound.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestIndexSortIntSortLongBound
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
