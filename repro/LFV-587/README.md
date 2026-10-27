# With NoMergeScheduler and a merge policy that selects merges (for example the default TieredMergePolicy), IndexWriter.close (default commit-on-close), forceMerge(1) and a blocking forceMergeDeletes never return: IndexWriter registers the policy's merges as pending, NoMergeScheduler never runs or removes them, and all three calls wait until no merge is pending

Bug LFV-587 · Area: index / IndexWriter merge waiting (close/shutdown waitForMerges, forceMerge, forceMergeDeletes) with NoMergeScheduler · Priority: low · Impact: hang · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You configure an IndexWriter with NoMergeScheduler, keep the default merge policy, index normally (for example 15 one-document commits) and then call close, forceMerge(1) or forceMergeDeletes. You expect close to commit and return without merging, since the NoMergeScheduler javadoc says it prevents merges 'regardless of the MergePolicy used', and the forced merges to return or fail. Instead each call waits forever: IndexWriter keeps registering the policy's merges as pending, NoMergeScheduler never takes them, and the calls re-check once a second in doWait until no merge is pending, which never happens.

## Expected vs actual

- Expected: You expect close to commit and return without merging, since the NoMergeScheduler javadoc says it prevents merges 'regardless of the MergePolicy used', and the forced merges to return or fail.
- Actual: Each call waits forever: IndexWriter keeps registering the policy's merges as pending, NoMergeScheduler never takes them, and the calls re-check once a second in doWait until no merge is pending, which never happens.

## Files

- [src/org/apache/lucene/index/TestNoMergeSchedulerWaits.java](src/org/apache/lucene/index/TestNoMergeSchedulerWaits.java) (JUnit test)
- [src2/org/apache/lucene/index/TestNoMergeSchedulerWaits.java](src2/org/apache/lucene/index/TestNoMergeSchedulerWaits.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestNoMergeSchedulerWaits
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestNoMergeSchedulerWaits
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/iw-forcemerge-wait-protocol-tla/](../../models/iw-forcemerge-wait-protocol-tla/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
