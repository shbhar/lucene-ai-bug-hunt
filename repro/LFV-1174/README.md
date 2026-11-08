# An Error while IndexWriter aborts the queued in-memory segments of a failed full flush leaves the unvisited segments registered as flushing, so DirectoryReader.open(writer) and the writer's own self-close after the Error wait forever in waitForFlush (every release since 7.4.0)

Finding LFV-1174 · Area: index / IndexWriter full-flush abort (DocumentsWriterFlushControl.abortPendingFlushes, doAfterFlush, waitForFlush) · Priority: low · Impact: hang · Novelty: new

Finding page: [../../bugs/LFV-1174.html](../../bugs/LFV-1174.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

Several threads index into one IndexWriter, so it holds three or more in-memory segments, and you open an NRT reader; the full flush fails, and while the writer aborts the segments still queued behind the failed one an Error (realistically an OutOfMemoryError) is thrown. You expect what the IndexWriter javadoc promises: after an Error the writer closes itself, DirectoryReader.open(writer) throws, and later calls throw AlreadyClosedException. Instead abortPendingFlushes catches only Exception, so the Error leaves its loop after releasing just the current segment, the segments it has not visited yet stay registered as flushing with nothing left to release them, and the writer's self-close waits for them forever, so DirectoryReader.open(writer) never returns.

## Expected vs actual

- Expected: `DirectoryReader.open(writer)` throws the `OutOfMemoryError` and `writer.isOpen()` is false.
- Actual: `DirectoryReader.open(writer)` never returns: `Test timeout exceeded (>= 20000 msec)`, parked in `DocumentsWriterFlushControl.waitForFlush:364`.

## Files

- [src/org/apache/lucene/index/TestErrorAbortingQueuedSegments.java](src/org/apache/lucene/index/TestErrorAbortingQueuedSegments.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed 5EED0001
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=3 -Dtests.seed=5EED0001 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestErrorAbortingQueuedSegments
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestErrorAbortingQueuedSegments.java](TestErrorAbortingQueuedSegments.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
