# IndexWriter accepts doc-values updates of the reserved parent field; with index sorting and document blocks this leaves a segment that fails CheckIndex, or an index whose merges fail

Bug LFV-095 · Area: index / IndexWriter doc-values updates · Priority: low · Impact: data corruption · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You use index sorting with a parent field (IndexWriterConfig#setParentField) and document blocks, and call IndexWriter#updateNumericDocValue or #updateDocValues on that parent field, for example to give a child document a parent mark or to set a parent's mark to null. You expect the call to be rejected with IllegalArgumentException, as IndexWriter rejects doc-values updates of index-sort fields and any document that contains the parent field ('is a reserved field'), or else the index to stay valid. Instead the update is accepted: a child given a mark can put the segment out of index-sort order, so CheckIndex fails ('segment has indexSort=&lt;long: "sort"&gt; but docID=1 sorts after docID=2') as soon as the update is committed, and a removed mark makes the next merge throw EOFException in MultiSorter and close the writer or, when the children have no sort value, silently moves the orphaned documents into the next block.

The test shows that with an index sort and `setParentField("_parent")`, `IndexWriter#updateNumericDocValue(id:child, "_parent", -1)` on a committed block [child(sort=9), parent(sort=1)] is accepted and leaves a segment out of index-sort order (`TestParentFieldDocValuesUpdate#testUpdateParentFieldOfChildKeepsIndexSorted`).

## Expected vs actual

Expected: the update is rejected with `IllegalArgumentException` or the segment stays sorted (`CheckIndex.testSort` error null). Actual: `expected null, but was:<CheckIndexException: segment has indexSort=<long: "sort"> but docID=0 sorts after docID=1>`, the same with -ea and -da.

## Files

- [src/org/apache/lucene/index/TestParentFieldDocValuesUpdate.java](src/org/apache/lucene/index/TestParentFieldDocValuesUpdate.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestParentFieldDocValuesUpdate
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
