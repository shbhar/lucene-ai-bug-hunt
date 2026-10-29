# OfflineSorter loses a partitionsInRAM permit whenever its ExecutorService rejects a partition-sort task, so a later sort on the same sorter waits forever even though the executor is idle (released since 6.6.0)

Bug LFV-384 · Area: util / OfflineSorter concurrent sort (partitionsInRAM permits on a rejected submit) · Priority: low · Impact: hang · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You give OfflineSorter your own bounded thread pool, one sort fails with RejectedExecutionException because the pool was full (or shut down), and you expect the same sorter to work again once the pool is idle, since every other exception path gives the partition's partitionsInRAM permit back. Instead the permit that readPartition took for the rejected partition is never released (only the task's finally block releases it, and a rejected task never runs; the semaphore belongs to the sorter instance), so with maxPartitionsInRAM = 1 the next sort on that sorter waits forever in Semaphore.acquire with an idle executor (executed: 'sort did not return within 20 s; waiting for a partition permit: true; executor active=0 queued=0'). With maxPartitionsInRAM = k each rejection costs one permit and after k rejections every later sort hangs; with a shut-down executor, a call that should fail fast with RejectedExecutionException waits instead.

The test shows that after one sort() fails with RejectedExecutionException (executor shut down, maxPartitionsInRAM = 1), a second sort() on the same OfflineSorter waits forever for the partitionsInRAM permit that the rejected task never released.

## Expected vs actual

Expected: the second sort() throws RejectedExecutionException again. Actual (-ea and -da): it is still blocked after 10 s.

## Files

- [src/org/apache/lucene/util/TestOfflineSorterRejectedSubmit.java](src/org/apache/lucene/util/TestOfflineSorterRejectedSubmit.java) (JUnit test)

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
     execute --select-class org.apache.lucene.util.TestOfflineSorterRejectedSubmit
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/offline-sorter/](../../models/offline-sorter/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
