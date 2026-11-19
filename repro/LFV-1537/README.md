# IndexSortSortedNumericDocValuesRangeQuery answers from its points shortcut on an index sorted by FLOAT or DOUBLE, where its javadoc says the search is delegated to the fallback query: with FloatPoint and FloatDocValuesField (or DoublePoint and DoubleDocValuesField) on the sort field, a query whose bounds are the doc-values bits of a negative value or -0.0 can silently miss its document or return another one (9.5.0 to 10.6.0, branch_11x and main; known upstream, #16629)

Finding LFV-1537 · Area: search / IndexSortSortedNumericDocValuesRangeQuery (index-sort range shortcut: the points path, getDocIdSetIteratorOrNullFromBkd) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You sort an index by new SortField("f", SortField.Type.FLOAT), index the float -1f with FloatPoint and FloatDocValuesField on "f", and search new IndexSortSortedNumericDocValuesRangeQuery("f", bits, bits, fallback), where bits is Float.floatToRawIntBits(-1f), the value FloatDocValuesField stores, and the fallback is NumericDocValuesField.newSlowExactQuery("f", bits). You expect the 1 document the fallback returns: the class javadoc says the optimized strategy is used only when the sort field is of type LONG or INT and that otherwise the search is delegated to the fallback. You get count 0 and no hit, with no error: the query asks the field's points before it checks the sort type, and it compares the doc-values bits with the points, which hold FloatPoint's sortable encoding, another number whenever the sign bit is set.

## Expected vs actual

- Expected: You expect the 1 document the fallback returns: the class javadoc says the optimized strategy is used only when the sort field is of type LONG or INT and that otherwise the search is delegated to the fallback.
- Actual: While the reported behaviour is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/search/TestIndexSortRangeQueryFloatSortWithPoints.java](src/org/apache/lucene/search/TestIndexSortRangeQueryFloatSortWithPoints.java) (JUnit test)

## Run it

- Tested on Lucene main 1ae70c6e45b8 (a commit of 2026-11-19): still reproduces.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestIndexSortRangeQueryFloatSortWithPoints
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
