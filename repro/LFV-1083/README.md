# If commit() fails with an InfoStream exception after the pending segments file is synced, the commit's file references are released twice, so a later rollback() (or close() with commitOnClose=false) deletes files of the last commit and the index can no longer be opened

Finding LFV-1083 · Area: index / IndexWriter commit failure handling (IndexWriter.startCommit, prepareCommitInternal and rollback; IndexFileDeleter reference counts) · Priority: medium · Impact: index unusable · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You enable an InfoStream on an IndexWriter, commit one document, add a second one and call commit() again, and the InfoStream throws a RuntimeException on the 'done all syncs' message that Lucene logs after it has written and fsynced pending_segments_2; commit() fails without closing the writer, so you call rollback(), whose javadoc says the index will then be as it was when commit() was last called. Instead, startCommit has kept the half-finished commit as its pending commit while prepareCommitInternal's catch has already released that commit's file references, and rollback() releases them a second time, so the file deleter removes _0.cfe, _0.cfs and _0.si, which segments_1 still needs. DirectoryReader.open(dir) then fails with CorruptSegmentInfoException (NoSuchFileException _0.si), close() with commitOnClose=false does the same, and with -ea rollback() throws AssertionError 'RefCount is 0 pre-decrement' instead.

## Expected vs actual

- Expected: `rollback()` returns the index to `segments_1`, and `DirectoryReader.open` sees 1 document.
- Actual: `CorruptSegmentInfoException: _0.si cannot be read`, caused by `NoSuchFileException: _0.si` (assertions off); `AssertionError: RefCount is 0 pre-decrement for file "_1.cfs"` in `rollback()` (assertions on).

## Files

- [src/org/apache/lucene/index/TestRollbackAfterFailedCommit.java](src/org/apache/lucene/index/TestRollbackAfterFailedCommit.java) (JUnit test)

## Run it

- Tested on Lucene main e1faeba08bde (2026-11-02): still reproduces.
- Recorded outcome of run 1 on Lucene main e1faeba08bde: 1 test(s) run, 1 failed (testRollbackKeepsLastCommit).
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

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestRollbackAfterFailedCommit
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
