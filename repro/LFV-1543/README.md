# If IndexWriter's InfoStream throws at the first log message of the rollback step ('rollback'), close() or rollback() throws and the writer is left closing, not closed: the write lock stays held and a later close() or rollback() waits until its thread is interrupted (run through rollback() on 25 releases from 2.9.4 to 10.6.0, on branch_11x and on main, and through close() on 10 of them from 4.10.0 on and on both branches; before 4.7.1 only a later close() waits; after a rollback() that threw while a prepareCommit() was pending, a later close() throws IllegalStateException instead of waiting when commit on close is on (the default; run on 10.6.0, branch_11x and main); the interrupt was run on 9.1.0, 10.6.0, branch_11x and main; needs an InfoStream that throws at that message)

Finding LFV-1543 · Area: index / IndexWriter close() and rollback() (rollbackInternalNoCommit: the first log statement, in front of the try; shouldClose and the closing flag; the write lock) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You turn on IndexWriter's InfoStream (its diagnostic log, off by default), add a document and close the writer, and the log call throws an exception at the message 'rollback', the first thing the writer logs when it starts to release its resources. You expect what the close() javadoc states for a close() that throws: the exception, a closed writer and a released write lock. Instead the writer stays in its closing state: the write lock is still held, a new IndexWriter on that directory fails with LockObtainFailedException, and a second close() or rollback() waits (run on branch_11x, main and 10.6.0: still waiting after 5 s; it ended when its thread was interrupted, and the lock was still held after that); the index is not damaged, and the commit that close() makes is already in it.

## Expected vs actual

- Expected: `close()` throws the stream's exception and leaves the writer closed, so `dir.obtainLock("write.lock")` succeeds.
- Actual: `org.apache.lucene.store.LockObtainFailedException: lock instance already obtained: (... lockName=write.lock)` at the test's line 48: the write lock is still held.

## Files

- [src/org/apache/lucene/index/TestCloseThrowsOnRollbackLogLine.java](src/org/apache/lucene/index/TestCloseThrowsOnRollbackLogLine.java) (JUnit test)

## Run it

- Tested on Lucene main 1ae70c6e45b8 (a commit of 2026-11-19): still reproduces.
- Tested on Lucene branch_11x 89e68e0f3a50 (a commit of 2026-11-17): reproduced.
- The test of this folder adds no document: it is a smaller form of the scenario above and checks two things, that close() throws a RuntimeException and that the write lock can then be obtained. A branch_11x checkout builds 11.0.0-SNAPSHOT jars, so set V=11.0.0-SNAPSHOT there.
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

# run 1: assertions off (production-like), seed 65B863
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=65B863 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestCloseThrowsOnRollbackLogLine
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

Formal models and scripts this finding's part of the hunt used: [../../models/indexwriter-rollback-throwing-infostream/](../../models/indexwriter-rollback-throwing-infostream/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
