# IndexWriter accepts doc-values updates of a field that a custom index-sort key reads (such as a BinarySortField subclass that derives its key from another doc-values field), so the segment keeps its old order while still declaring the index sort: CheckIndex rejects it, sorted searches that terminate early return wrong top hits, and merges carry the disorder forward

Bug LFV-759 · Area: index / IndexWriter doc-values updates with index sorting (BinarySortField, custom IndexSorter) · Priority: low · Impact: data corruption · Novelty: new

Bug page: [../../bugs/LFV-759.html](../../bugs/LFV-759.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You configure an index sort with a BinarySortField subclass whose key is derived from another doc-values field (sort by the numeric field price, ties broken by the binary field sku, declared on sku), index a(5), b(7), c(8), and then change c's price to 1 with updateNumericDocValue; you expect the writer either to reject the update, as it rejects an update of sku itself ('cannot update docvalues field involved in the index sort'), or to keep the segment sorted. Instead the update is accepted, the segment stays in the order a, b, c while still declaring the sort, CheckIndex reports 'segment has indexSort=&lt;binary: "sku"&gt; but docID=1 sorts after docID=2', and a top-1 search sorted by the index sort returns a instead of c because it stops at the first non-competitive document. The cause is that IndexWriterConfig#setIndexSort protects only the SortField#getField names, while a custom index sort may read any field.

IndexWriter accepts `updateNumericDocValue` of the field a BinarySortField subclass derives its index-sort key from, so the segment keeps its old order while still declaring the sort.

## Expected vs actual

Expected: the update is rejected (as it is for the sort's own field) or the segment stays sorted; actual: CheckIndex reports `segment has indexSort=<binary: "sku"> but docID=0 sorts after docID=1`.

## Files

- [src/org/apache/lucene/index/TestDerivedSortKeyDocValuesUpdate.java](src/org/apache/lucene/index/TestDerivedSortKeyDocValuesUpdate.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestDerivedSortKeyDocValuesUpdate
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
