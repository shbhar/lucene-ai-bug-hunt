# IndexWriter.close never returns when a background merge fails while a forced-merge cascade merge is still pending (forceMerge(n, false), then close): the tragic rollback is skipped because close is already closing, getNextMerge refuses the pending merge once the tragedy is set, and waitForMerges never checks the tragedy (since 9.3.0)

Bug LFV-648 · Area: index / IndexWriter close (waitForMerges, ConcurrentMergeScheduler, tragic merge failure) · Priority: low · Impact: hang · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You start a background forced merge with forceMerge(1, false) and then call close, and you expect close to commit and return, or, if a merge fails, to throw and leave the writer closed, as its javadoc says. Instead, if one merge fails (for example disk full) just after another forced merge finished and queued the next round, close waits forever in waitForMerges: the failure does not roll the writer back because close is already closing, since 9.3.0 IndexWriter refuses to start any pending merge after such a failure, and the wait loop never looks at the failure. The write lock stays held and a rollback from another thread blocks as well; only interrupting the closing thread ends the wait.

## Expected vs actual

- Expected: You expect close to commit and return, or, if a merge fails, to throw and leave the writer closed, as its javadoc says.
- Actual: If one merge fails (for example disk full) just after another forced merge finished and queued the next round, close waits forever in waitForMerges: the failure does not roll the writer back because close is already closing, since 9.3.0 IndexWriter refuses to start any pending merge after such a failure, and the wait loop never looks at the failure.

## Files

- [src/org/apache/lucene/index/TestCloseAfterTragicMergeWithPendingMerge.java](src/org/apache/lucene/index/TestCloseAfterTragicMergeWithPendingMerge.java) (JUnit test)
- [src2/org/apache/lucene/index/TestCloseAfterTragicMergeWithPendingMerge.java](src2/org/apache/lucene/index/TestCloseAfterTragicMergeWithPendingMerge.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
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
     execute --select-class org.apache.lucene.index.TestCloseAfterTragicMergeWithPendingMerge
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestCloseAfterTragicMergeWithPendingMerge
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/iw-blocking-calls-watchdog-failure-visibility/](../../models/iw-blocking-calls-watchdog-failure-visibility/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
