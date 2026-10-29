# A SORTED_SET DictionaryColumn tuple that addBatch rejects (out-of-range ordinal, or ordValue throws) leaves an empty document in the doc-values buffer, so the next flush fails and closes the IndexWriter, or with -da commits a segment whose field cannot be read

Bug LFV-094 · Area: index / columnar addBatch (DictionaryColumn SORTED_SET doc values, SortedSetDocValuesWriter) · Priority: medium · Impact: data corruption · Novelty: new

Bug page: [../../bugs/LFV-094.html](../../bugs/LFV-094.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You add a batch with IndexWriter#addBatch whose SORTED_SET DictionaryColumn gives the first value of some document an ordinal outside the dictionary (or whose cursor throws while returning that ordinal); Lucene rejects the batch with an IllegalArgumentException, the writer stays open with no tragic exception, and you keep indexing. You expect the rejected batch to leave no trace, as Lucene promises for non-aborting exceptions (the batch's documents are marked deleted and the index stays consistent), so a later document with two SORTED_SET values commits and reads back normally. Instead the doc-values writer had already opened the rejected document before it checked the ordinal and records it with zero values, so the next commit fails (AssertionError with -ea, ArrayIndexOutOfBoundsException with -da), closes the writer and loses all uncommitted documents, or, with -da and some value layouts, succeeds but writes a segment whose field throws NullPointerException when opened or fails CheckIndex.

The test shows that after `IndexWriter#addBatch` rejects a SORTED_SET `DictionaryColumn` whose only tuple has an out-of-range ordinal, committing a later ordinary document with two SORTED_SET values fails.

## Expected vs actual

Expected: the commit succeeds (the rejected batch leaves no trace); actual: the rejected document was already opened and is flushed with zero values, so commit throws (AssertionError `ordCount > 0` with -ea, ArrayIndexOutOfBoundsException with -da).

## Files

- [src/org/apache/lucene/index/TestRejectedSortedSetOrdinalBreaksCommit.java](src/org/apache/lucene/index/TestRejectedSortedSetOrdinalBreaksCommit.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestRejectedSortedSetOrdinalBreaksCommit
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/columnar-addbatch/](../../models/columnar-addbatch/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
