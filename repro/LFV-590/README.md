# IndexWriter.forceMerge(n) can return normally with more than n segments when a merge that was already pending or running at the call fails: forceMerge adopts that merge without updating its mergeGen, so addMergeException drops the failure, and the tragic event is published only after mergeFinish has woken the waiting forceMerge

Bug LFV-590 · Area: index / IndexWriter forced merge (forceMerge wait loop, mergeExceptions and mergeGen, tragic events) · Priority: medium · Impact: wrong error · Novelty: new

Bug page: [../../bugs/LFV-590.html](../../bugs/LFV-590.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call forceMerge(1) while a background merge started by an earlier commit or flush (or by forceMerge's own flush) is still pending or running, and that merge fails, for example because the disk is full. You expect forceMerge to throw, as it does ('background merge hit exception') when the failing merge is one it registered itself. Instead it can return normally with the index still unmerged (2 segments, or 9 with the default TieredMergePolicy): the adopted merge keeps its old mergeGen, so its failure is dropped, and the tragic event that closes the writer is published only after mergeFinish has woken the waiting call.

## Expected vs actual

- Expected: `forceMerge(1)` throws, as the control does (`IOException: background merge hit exception`), or leaves 1 segment.
- Actual: `forceMerge(1)` returns normally with 2 segments (9 with the default policy); the writer then closes itself, and `close` returns normally with 1 of 2 documents left.

## Files

- [src/org/apache/lucene/index/TestForceMergeAdoptedMergeFailure.java](src/org/apache/lucene/index/TestForceMergeAdoptedMergeFailure.java) (JUnit test)
- [src/org/apache/lucene/index/TestForceMergeAdoptedMergeFailureThenClose.java](src/org/apache/lucene/index/TestForceMergeAdoptedMergeFailureThenClose.java) (JUnit test)

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

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestForceMergeAdoptedMergeFailure

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestForceMergeAdoptedMergeFailureThenClose
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestForceMergeAdoptedMergeFailure.java](TestForceMergeAdoptedMergeFailure.java)
- [PublicApiForceMergeRace.java](PublicApiForceMergeRace.java)
- [TestForceMergeAdoptedMergeFailureThenClose.java](TestForceMergeAdoptedMergeFailureThenClose.java)

Formal models and scripts this bug's part of the hunt used: [../../models/iw-forcemerge-wait-protocol-tla/](../../models/iw-forcemerge-wait-protocol-tla/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
