# If a OneMerge.mergeFinished override throws for a merge that was aborted before mergeMiddle started, IndexWriter.merge never removes the merge from runningMerges, so rollback() (and deleteAll()) never return (8.6.0 to main)

Finding LFV-1402 · Area: index / IndexWriter merge lifecycle (IndexWriter.merge finally, mergeFinish, runningMerges, abortMerges) · Priority: low · Impact: hang · Novelty: new

Finding page: [../../bugs/LFV-1402.html](../../bugs/LFV-1402.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You give IndexWriter a merge policy that returns its own OneMerge whose mergeFinished(boolean, boolean) override throws an IOException (the method is public and declared to throw it), let a merge start, and call rollback() before the merge thread reaches mergeMiddle. You expect rollback() to close the writer without committing, as documented, after the merge thread has reported the hook's exception. Instead IndexWriter.merge's finally calls closeMergeReaders, which runs mergeFinished, before mergeFinish(merge) with no nested finally, so the merge is never removed from runningMerges and rollback() waits forever in abortMerges; deleteAll() hangs the same way.

## Expected vs actual

- Expected: `maybeMerge()` throws `IOException("mergeFinished failed")`, then `rollback()` closes the writer and the test passes (0.12 s with the fix).
- Actual: `rollback()` does not return (stuck in `IndexWriter.abortMerges`), so after 10 s the test fails with "rollback() did not return", assertions on and off.

## Files

- [src/org/apache/lucene/index/TestRollbackWithThrowingMergeFinished.java](src/org/apache/lucene/index/TestRollbackWithThrowingMergeFinished.java) (JUnit test)

## Run it

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

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestRollbackWithThrowingMergeFinished
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestRollbackWithThrowingMergeFinished.java](TestRollbackWithThrowingMergeFinished.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
