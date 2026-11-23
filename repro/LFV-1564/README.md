# With SerialMergeScheduler, a rollback() whose wait for a running merge is cut short by an interrupt can close the writer under that merge (run with a merge inside another thread's forceMerge(1) or flush() call): the merge then fails before it releases its segment readers, and the files they had open stay open after the writer is closed (known upstream: the interrupted wait is described on #6947)

Finding LFV-1564 · Area: index / IndexWriter rollback() against a running merge (the interruptible wait of abortMerges, the failure branch of rollbackInternalNoCommit, closeMergeReaders; SerialMergeScheduler) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run an IndexWriter with SerialMergeScheduler, so a merge runs inside the call that started it (forceMerge(1) in the test), and while that merge has its segment readers open a second thread calls rollback(), whose wait for the merge to abort is cut short by an interrupt (in the test the calling thread's interrupt status is set when it calls rollback(), so the wait ends at once; an earlier test interrupted the thread from outside once it waited, with the same outcome). You expect that, once both calls have ended, the writer and its merge hold no file of the index open (the test has no reader of its own), as after the same rollback() without the interrupt. Instead the test's Directory.close() fails with 'MockDirectoryWrapper: cannot close: there are still 2 open files: {_1.cfs=1, _0.cfs=1}': rollback() closed the reader pool and the writer under the merge, and the merge's clean-up then failed before it released its two segment readers (the test requires that rollback() throws ThreadInterruptedException and that forceMerge(1) throws AlreadyClosedException). Not new in 11: 10.6.0 does the same.

## Expected vs actual

- Expected: You expect that, once both calls have ended, the writer and its merge hold no file of the index open (the test has no reader of its own), as after the same rollback() without the interrupt.
- Actual: The test's Directory.close() fails with 'MockDirectoryWrapper: cannot close: there are still 2 open files: {_1.cfs=1, _0.cfs=1}': rollback() closed the reader pool and the writer under the merge, and the merge's clean-up then failed before it released its two segment readers (the test requires that rollback() throws ThreadInterruptedException and that forceMerge(1) throws AlreadyClosedException).

## Files

- [src/org/apache/lucene/index/TestInterruptedRollbackLeavesMergeReadersOpen.java](src/org/apache/lucene/index/TestInterruptedRollbackLeavesMergeReadersOpen.java) (JUnit test)

## Run it

- Tested on Lucene main 11c8b6ea99ae (a commit of 2026-11-23): still reproduces.
- Tested on Lucene branch_11x 75eea467ac52 (a commit of 2026-11-19): reproduced.
- A branch_11x checkout builds 11.0.0-SNAPSHOT jars, so set V=11.0.0-SNAPSHOT there.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual, or names the exception that was thrown).
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

# run 1: assertions off (production-like), seed C0FFEE907
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=C0FFEE907 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestInterruptedRollbackLeavesMergeReadersOpen
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

Formal models and scripts this finding's part of the hunt used: [../../models/merge-readers-left-open-after-writer-close/](../../models/merge-readers-left-open-after-writer-close/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
