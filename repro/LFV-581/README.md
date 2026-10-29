# With SerialMergeScheduler, a merge that fails while another thread's commit waits to run its merge-on-commit merges deadlocks both threads forever (commit holds commitLock and waits for the scheduler's monitor; the failing merge holds that monitor and its tragic rollback waits for commitLock), and the writer can no longer be closed (since 8.6.0; with default merge-on-commit settings since 9.3.0)

Bug LFV-581 · Area: index / IndexWriter commit (merge-on-commit under commitLock, SerialMergeScheduler, tragic-event rollback) · Priority: low · Impact: hang · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You configure IndexWriter with SerialMergeScheduler and keep merge-on-commit on (the default since 9.3); one thread calls commit while another thread's addDocument runs a merge, and that merge fails, for example because the disk is full. You expect IndexWriter to close itself on the tragic merge failure and both calls to return with an exception, as they do with ConcurrentMergeScheduler or with merge-on-commit off. Instead commit holds IndexWriter's commit lock while it waits to enter SerialMergeScheduler's synchronized merge method, and the failing merge thread, still inside that method, waits for the commit lock in the tragic rollback, so both threads hang forever and the writer cannot be closed.

The test shows that with SerialMergeScheduler and the default merge-on-commit wait, a merge that fails on another thread while commit() starts its merge-on-commit merges deadlocks commit() and the merging thread (commit holds commitLock and waits for the scheduler monitor; the failing merge holds that monitor and its tragic rollback waits for commitLock).

## Expected vs actual

Expected commit() and the failed merge to both return with an exception (the writer closes on the tragic merge failure); actual: the JVM deadlock detector lists both threads (`expected:<[]> but was:<[committer, merger]>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/index/TestSerialMergeSchedulerCommitDeadlock.java](src/org/apache/lucene/index/TestSerialMergeSchedulerCommitDeadlock.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestSerialMergeSchedulerCommitDeadlock
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
