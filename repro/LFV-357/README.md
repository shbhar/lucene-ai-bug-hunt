# OfflineSorter.sort with an ExecutorService runs its failure cleanup before the partition-sort and merge tasks it already submitted have finished, so each task still queued or running leaves a temp file (prefix_sort_N.tmp) behind that nothing deletes (released since 6.6.0)

Bug LFV-357 · Area: util / OfflineSorter concurrent sort (temp-file cleanup on failure) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You give OfflineSorter your own ExecutorService so it sorts partitions in parallel, and when sort fails (an I/O error, a truncated input file, or an interrupt of the sorting thread) you expect it to delete every temp file it created, as it does without an executor and as its own code comment says ('So we can remove any partially written temp files on exception'). Instead sort deletes only the temp files that exist at that moment and rethrows, without waiting for or cancelling the partition-sort and merge tasks it already handed to the executor. Those tasks then run and each writes a new prefix_sort_N.tmp file that nothing ever deletes (executed: the directory holds no sorter file right after sort throws, and foo_sort_1.tmp once the queued task has run; with exec == null the same failure leaves nothing).

## Expected vs actual

- Expected: You expect it to delete every temp file it created, as it does without an executor and as its own code comment says ('So we can remove any partially written temp files on exception').
- Actual: sort deletes only the temp files that exist at that moment and rethrows, without waiting for or cancelling the partition-sort and merge tasks it already handed to the executor.

## Files

- [src/org/apache/lucene/util/TestOfflineSorterFailureCleanup.java](src/org/apache/lucene/util/TestOfflineSorterFailureCleanup.java) (JUnit test)
- [src2/org/apache/lucene/util/TestOfflineSorterFailureCleanup.java](src2/org/apache/lucene/util/TestOfflineSorterFailureCleanup.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
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

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.TestOfflineSorterFailureCleanup
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.TestOfflineSorterFailureCleanup
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/offline-sorter/](../../models/offline-sorter/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
