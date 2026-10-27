# A failed addIndexes(CodecReader...) leaves its unstarted merges in the writer-wide addIndexes queue, so the next addIndexes call runs them on the earlier call's readers and fails with AlreadyClosedException if those readers were closed

Bug LFV-436 · Area: index / IndexWriter addIndexes(CodecReader...) failure path (AddIndexesMergeSource queue, SerialMergeScheduler) · Priority: low · Impact: crash · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call addIndexes(CodecReader...) with a merge policy that turns the readers into several merges and with SerialMergeScheduler, and the first merge fails (for example disk full); the call throws, adds nothing, and the writer stays open and not tragic. You expect that failed call to be over, so you close its readers and later call addIndexes with other, valid readers, which should merge exactly those readers. Instead the failed call's unstarted merges are still in the writer's shared addIndexes queue, so the next call runs them first and throws AlreadyClosedException 'this IndexReader is closed', leaving its own merge queued in turn, so later calls keep failing this way until rollback or close; if you kept the old readers open, the next call silently runs the stale merge and writes a segment that is never used.

## Expected vs actual

- Expected: You expect that failed call to be over, so you close its readers and later call addIndexes with other, valid readers, which should merge exactly those readers.
- Actual: The failed call's unstarted merges are still in the writer's shared addIndexes queue, so the next call runs them first and throws AlreadyClosedException 'this IndexReader is closed', leaving its own merge queued in turn, so later calls keep failing this way until rollback or close; if you kept the old readers open, the next call silently runs the stale merge and writes a segment that is never used.

## Files

- [src/org/apache/lucene/index/TestFailedAddIndexesLeavesQueuedMerges.java](src/org/apache/lucene/index/TestFailedAddIndexesLeavesQueuedMerges.java) (JUnit test)

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

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestFailedAddIndexesLeavesQueuedMerges
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
