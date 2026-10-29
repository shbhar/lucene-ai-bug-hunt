# addIndexes(CodecReader...) throws AlreadyClosedException 'this IndexWriter is closed' on an open writer when it overlaps a deleteAll that is waiting for a running merge to abort, although deleteAll's javadoc says such callers may receive MergeAbortedException

Bug LFV-632 · Area: index / IndexWriter addIndexes(CodecReader...) entry check vs deleteAll (abortMerges) · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

One thread calls deleteAll on an IndexWriter while a merge is running, and another thread calls addIndexes(CodecReader...) on the same writer. You expect the overlapping call either to succeed or to fail with MergePolicy.MergeAbortedException, as deleteAll's javadoc says for concurrent addIndexes(CodecReader) callers and as most such overlaps do. Instead, when the call reaches its entry check while deleteAll is waiting for the running merge to abort, it throws AlreadyClosedException 'this IndexWriter is closed. Cannot execute addIndexes(CodecReaders...) API' although the writer is open: isOpen is true, deleteAll returns normally and the next addIndexes call on the same writer succeeds.

The test shows that an `addIndexes(CodecReader...)` call that has passed its flush reaches its merges-enabled check while `deleteAll()` waits for a running merge to abort, and it throws `AlreadyClosedException` although the writer is open.

## Expected vs actual

Expected, per the `deleteAll()` javadoc: success or `MergePolicy.MergeAbortedException`. Actual: `AlreadyClosedException: this IndexWriter is closed. Cannot execute addIndexes(CodecReaders...) API` with `isOpen()=true`.

## Files

- [src/org/apache/lucene/index/TestAddIndexesDuringDeleteAll.java](src/org/apache/lucene/index/TestAddIndexesDuringDeleteAll.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestAddIndexesDuringDeleteAll
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
