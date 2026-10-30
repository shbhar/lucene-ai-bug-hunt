# Doc-values exact and range queries silently miss or add documents on a skip-indexed field that holds raw float/double bits and that the index is sorted by as FLOAT or DOUBLE: SortedNumericDocValuesRangeQuery's index-sort shortcut checks the sort field's name but not its type

Bug LFV-1047 · Area: search / doc-values range queries, index-sort shortcut (SortedNumericDocValuesRangeQuery.canUsePrimarySortShortcut, SortedSkipperScorerSupplier) · Priority: medium · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index the floats -1f and -2f as raw bits in a skip-indexed doc-values field (FloatDocValuesField.indexedField("f", Float.floatToRawIntBits(v))), sort the index by that field with new SortField("f", SortField.Type.FLOAT), and count the documents matching NumericDocValuesField.newSlowExactQuery("f", Float.floatToRawIntBits(-1f)). You expect 1, which is what you get without the index sort or without the skip index. You get 0: the query's index-sort shortcut computes one contiguous doc-ID range as if the documents were in the order of their stored longs, but the FLOAT sort puts -2f (raw -1073741824) before -1f (raw -1082130432), the reverse of the long order, and the shortcut never checks the sort type.

## Expected vs actual

- Expected: You expect 1, which is what you get without the index sort or without the skip index.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/document/TestDocValuesRangeQueryFloatIndexSort.java](src/org/apache/lucene/document/TestDocValuesRangeQueryFloatIndexSort.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.document.TestDocValuesRangeQueryFloatIndexSort
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/dv-range-primary-sort-shortcut-ignores-sort-type/](../../models/dv-range-primary-sort-shortcut-ignores-sort-type/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
