# When a merge fails to open the refreshed reader of a segment with doc-values updates, IndexWriter never releases its reference on the pooled segment reader, so the segment's files stay open after IndexWriter.close

Bug LFV-446 · Area: index / IndexWriter merge-reader acquisition (ReadersAndUpdates.getReaderForMerge) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You update a doc-values field (or use soft deletes) and open NRT readers from your IndexWriter, then delete a document, and a merge (a merge-on-commit, a merge-on-refresh, or an ordinary or forced merge) has to open a fresh copy of that segment's pooled reader with the new deletions, and opening it fails with an IOException. You expect the merge to fail cleanly and IndexWriter to give back every reader reference it took, so that once the writer is closed no file of the index is still open. Instead ReadersAndUpdates.getReaderForMerge never releases the reference it took on the pooled reader, so that segment's compound file and doc-values update files stay open after IndexWriter.close, even when the failure was not tragic and the writer went on to commit and close normally.

The test shows that one segment with a doc-values update, pooled by an NRT reader, gets a delete; `forceMerge(1)` must open a refreshed reader for it and that open hits an injected `IOException` (fired while `ReadersAndUpdates.getReaderForMerge` is on the stack); `getReaderForMerge` never releases its reference on the pooled reader.

## Expected vs actual

Expected no open file once `IndexWriter.close()` has returned; actual: `MockDirectoryWrapper.close()` reports 4 open files `{_0.cfs, _0_1_Lucene90_0.dvp,.dvs,.dvd}`.

## Files

- [src/org/apache/lucene/index/TestMergeReaderRefreshFailureLeak.java](src/org/apache/lucene/index/TestMergeReaderRefreshFailureLeak.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestMergeReaderRefreshFailureLeak
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
