# A failed IndexRearranger.execute throws without waiting for its other tasks, and closes the input reader and the IndexWriter while those tasks still use them

Bug LFV-1011 · Area: misc / index tools (IndexRearranger.execute failure path: the await loops of createRearrangedIndex and applyDeletes inside the try-with-resources) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run IndexRearranger.execute with several segment selectors and one of the tasks fails (its selector throws an IOException, or addIndexes hits an I/O error). You expect execute to throw that error only after the tasks it started have stopped using its input reader and output IndexWriter. Instead execute rethrows the first failed Future.get at once and its try-with-resources closes the reader and the writer while the other tasks still run, so their selector code keeps running after execute has thrown and fails with AlreadyClosedException 'this IndexReader is closed', and an addIndexes merge that is still running dies on a merge thread with an uncaught MergeException.

The test shows that a failed `IndexRearranger.execute()` rethrows the first selector's failure at once and closes its input reader while a second selector of the same call is still running on it.

## Expected vs actual

How: the second selector keeps running until the caller either closes the reader under it (the bug) or stops running to wait for it (the fix). It starts this check once the caller holds the first failure: `Future.get()` builds `new ExecutionException(cause)`, which calls the failure's `toString()`. No sleeps or timeouts. Needs at least 2 processors (pool = min(processors, selectors)), otherwise it is skipped.

## Files

- [src/org/apache/lucene/misc/index/TestIndexRearrangerFailedExecute.java](src/org/apache/lucene/misc/index/TestIndexRearrangerFailedExecute.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/misc/build/libs/lucene-misc-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.misc.index.TestIndexRearrangerFailedExecute
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/indexrearranger-and-pkindexsplitter-failed-run-cleanup/](../../models/indexrearranger-and-pkindexsplitter-failed-run-cleanup/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
