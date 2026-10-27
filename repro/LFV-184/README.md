# addIndexes(CodecReader...) can register a merge and start a merge thread after a concurrent rollback has returned, because AddIndexesMergeSource.registerMerge does not re-check that merges are enabled

Bug LFV-184 · Area: index / IndexWriter addIndexes(CodecReader...) vs rollback (AddIndexesMergeSource, ConcurrentMergeScheduler) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call addIndexes(CodecReader...) on one thread while another thread calls rollback on the same writer (or the writer closes itself after a tragic error). You expect that once rollback has returned the writer is closed and starts no more merges, so the addIndexes call just fails; Lucene's own TestAddIndexes rollback tests assert that no merge thread is left. Instead, if rollback lands while addIndexes is asking the merge policy for its merges, the merge is registered after rollback has already aborted all merges, and ConcurrentMergeScheduler starts a new merge thread after rollback and the scheduler's close have returned; that thread throws an uncaught MergeException (AlreadyClosedException 'Lock instance already released' on 10.3.0 and later, 'this IndexWriter is closed' on 9.4.0-10.2.2) and addIndexes throws AlreadyClosedException.

## Expected vs actual

- Expected: You expect that once rollback has returned the writer is closed and starts no more merges, so the addIndexes call just fails; Lucene's own TestAddIndexes rollback tests assert that no merge thread is left.
- Actual: If rollback lands while addIndexes is asking the merge policy for its merges, the merge is registered after rollback has already aborted all merges, and ConcurrentMergeScheduler starts a new merge thread after rollback and the scheduler's close have returned; that thread throws an uncaught MergeException (AlreadyClosedException 'Lock instance already released' on 10.3.0 and later, 'this IndexWriter is closed' on 9.4.0-10.2.2) and addIndexes throws AlreadyClosedException.

## Files

- [src/org/apache/lucene/index/TestAddIndexesMergeAfterRollback.java](src/org/apache/lucene/index/TestAddIndexesMergeAfterRollback.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestAddIndexesMergeAfterRollback
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
