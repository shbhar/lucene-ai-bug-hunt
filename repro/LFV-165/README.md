# With an index sort, the flush-time iterator that IndexWriter hands to a doc-values or norms consumer for a numeric field that every document has ends intoBitSet(upTo &gt;= maxDoc) on docID == maxDoc instead of NO_MORE_DOCS, so a custom consumer that bulk-loads it and loops until NO_MORE_DOCS never finishes (latent: Lucene's own consumers skip the bulk load for such fields)

Bug LFV-165 · Area: index / doc values and norms flush with index sorting (NumericDocValuesWriter.SortingNumericDocValues) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You sort an index at flush and every document in the segment has a given numeric doc-values field (or norms, or a single-valued SortedNumericDocValuesField); a custom DocValuesFormat or NormsFormat consumer bulk-loads the iterator it gets at flush with intoBitSet(maxDoc) or intoBitSet(NO_MORE_DOCS) and expects it to end on NO_MORE_DOCS, as the reference loop in the intoBitSet javadoc does. Instead NumericDocValuesWriter.SortingNumericDocValues sets the right bits but leaves docID at maxDoc, a position the docID contract never allows, so a consumer that loops until NO_MORE_DOCS never ends; fed to IndexedDISI.writeBitSet it fails the flush with an AssertionError (-ea) or keeps writing empty-block headers until the output fails (-da). Lucene's own consumers never bulk-load a field that every document has, so stock indexes and search results are not affected.

The test shows that with an index sort, the flush-time iterator IndexWriter hands a norms (or numeric doc-values) consumer for a field every document has (`NumericDocValuesWriter.SortingNumericDocValues`) ends `intoBitSet(maxDoc,...)` on `docID() == maxDoc`.

## Expected vs actual

Expected: `docID() == NO_MORE_DOCS` (2147483647), as the `DocIdSetIterator#intoBitSet` reference loop ends. Actual: `2` (= maxDoc), with -ea and -da.

## Files

- [src/org/apache/lucene/index/TestSortedFlushIntoBitSetEndPosition.java](src/org/apache/lucene/index/TestSortedFlushIntoBitSetEndPosition.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestSortedFlushIntoBitSetEndPosition
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/indexeddisi-dv-bulk/](../../models/indexeddisi-dv-bulk/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
