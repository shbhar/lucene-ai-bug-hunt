# With SerialMergeScheduler, a valid addIndexes(CodecReader...) call can run a concurrent call's queued merge on its own thread and fail with that merge's exception, discarding its own completed merge

Bug LFV-629 · Area: index / IndexWriter addIndexes(CodecReader...) (writer-wide AddIndexesMergeSource queue, SerialMergeScheduler) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

Two threads call addIndexes(CodecReader...) on one IndexWriter configured with SerialMergeScheduler; call A's source is corrupt (or hits a read error) and call B's source is valid. You expect A to fail with its own exception and B to add its documents, as happens in either serial order and with the default ConcurrentMergeScheduler, because IndexWriter is documented as safe for concurrent calls to any method and each addIndexes call already rethrows the failures of its own merges. Instead B, which holds the scheduler, runs A's queued merge on its own thread after finishing its own, receives A's exception (for example CorruptIndexException 'checksum failed (hardware problem?)' naming A's source file), deletes its own completed merge and adds nothing, while A throws the same exception.

## Expected vs actual

- Expected: You expect A to fail with its own exception and B to add its documents, as happens in either serial order and with the default ConcurrentMergeScheduler, because IndexWriter is documented as safe for concurrent calls to any method and each addIndexes call already rethrows the failures of its own merges.
- Actual: B, which holds the scheduler, runs A's queued merge on its own thread after finishing its own, receives A's exception (for example CorruptIndexException 'checksum failed (hardware problem?)' naming A's source file), deletes its own completed merge and adds nothing, while A throws the same exception.

## Files

- [src/org/apache/lucene/index/TestAddIndexesSerialSchedulerForeignFailure.java](src/org/apache/lucene/index/TestAddIndexesSerialSchedulerForeignFailure.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestAddIndexesSerialSchedulerForeignFailure
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
