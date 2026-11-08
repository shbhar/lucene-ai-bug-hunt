# Doc-values range queries throw NullPointerException on a leaf whose index sort starts with a DOC or SCORE sort field (for example a custom reader that reports Sort.INDEXORDER), because the primary-sort shortcut calls getField().equals(field) on the null-field SortField that Sort.getPrimarySortField returns (SortedSetDocValuesRangeQuery and SortedNumericDocValuesRangeQuery since 10.0.0, IndexSortSortedNumericDocValuesRangeQuery since 9.5.0)

Finding LFV-1205 · Area: search / doc-values range queries, index-sort primary-sort shortcut (SortedSetDocValuesRangeQuery, SortedNumericDocValuesRangeQuery, IndexSortSortedNumericDocValuesRangeQuery, Sort.getPrimarySortField) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1205.html](../../bugs/LFV-1205.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You wrap a segment in a FilterLeafReader whose getMetaData() reports Sort.INDEXORDER (true for any segment, whose documents are in index order) and run SortedDocValuesField.newSlowRangeQuery("f", v2, v5) on a skip-indexed field; you expect the 4 matching documents, as on the unwrapped segment. Instead IndexSearcher.search throws NullPointerException 'Cannot invoke "String.equals(Object)" because the return value of "org.apache.lucene.search.SortField.getField()" is null': the query's index-sort shortcut asks Sort.getPrimarySortField for the leaf's primary sort field, which returns the DOC sort field (whose field name is null) on purpose, and then calls getField().equals(field) on it instead of skipping the shortcut. The same unguarded call is in SortedNumericDocValuesRangeQuery and IndexSortSortedNumericDocValuesRangeQuery.

## Expected vs actual

- Expected: `count` returns 2 (documents `b` and `c`), as on the unwrapped segment.
- Actual: `NullPointerException: Cannot invoke "String.equals(Object)" because the return value of "org.apache.lucene.search.SortField.getField()" is null` at `SortedSetDocValuesRangeQuery.canUsePrimarySortShortcut(SortedSetDocValuesRangeQuery.java:291)`.

## Files

- [src/org/apache/lucene/document/TestDocValuesRangeQueryIndexOrderSort.java](src/org/apache/lucene/document/TestDocValuesRangeQueryIndexOrderSort.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed 43DB801B77712C11
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=3 -Dtests.seed=43DB801B77712C11 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.document.TestDocValuesRangeQueryIndexOrderSort
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestDocValuesRangeQueryIndexOrderSort.java](TestDocValuesRangeQueryIndexOrderSort.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
