# Since 9.4.0, IndexWriter.addIndexes(CodecReader...) blocks indefinitely (until another thread interrupts it or rolls the writer back) when the writer uses NoMergeScheduler, also together with NoMergePolicy as the NoMergeScheduler javadoc recommends: the reader merge is handed to the merge scheduler, which never runs it, and is then awaited with no time limit

Bug LFV-586 · Area: index / IndexWriter addIndexes(CodecReader...) (merge-scheduler hand-off, NoMergeScheduler) · Priority: low · Impact: hang · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You configure an IndexWriter with NoMergeScheduler (also with NoMergePolicy, the pairing the NoMergeScheduler javadoc recommends) and call addIndexes(CodecReader...). You expect the call to add the readers, as it did up to 9.3.0, or at least to return or throw. Instead, since 9.4.0 (LUCENE-10216) the call hands its reader merge to the merge scheduler and then waits for it with no time limit; NoMergeScheduler.merge does nothing, so the call blocks until another thread interrupts it or rolls the writer back.

## Expected vs actual

- Expected: You expect the call to add the readers, as it did up to 9.3.0, or at least to return or throw.
- Actual: Since 9.4.0 (LUCENE-10216) the call hands its reader merge to the merge scheduler and then waits for it with no time limit; NoMergeScheduler.merge does nothing, so the call blocks until another thread interrupts it or rolls the writer back.

## Files

- [src/org/apache/lucene/index/TestAddIndexesNoMergeScheduler.java](src/org/apache/lucene/index/TestAddIndexesNoMergeScheduler.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestAddIndexesNoMergeScheduler
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/iw-forcemerge-wait-protocol-tla/](../../models/iw-forcemerge-wait-protocol-tla/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
