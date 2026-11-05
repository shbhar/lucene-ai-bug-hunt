# If aborting one in-memory segment throws during IndexWriter.rollback() or the tragic self-close, DocumentsWriter.abort() stops: the other buffered segments are never aborted, stay locked, and keep their stored-fields outputs open (a permanent leak since 4.7.1)

Finding LFV-1309 · Area: index / IndexWriter rollback (DocumentsWriter.abort over the per-thread pool) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1309.html](../../bugs/LFV-1309.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

Two threads add documents to one IndexWriter, so it buffers two in-memory segments, and then you call rollback(); while the first segment is being aborted an exception is thrown (here by the writer's InfoStream). You expect rollback() to pass that exception on but still release everything, which is what Lucene's own TestIndexWriterExceptions#testExceptionDuringRollback requires ('we are closed, no locks or files held'). Instead DocumentsWriter.abort() stops at the failing segment: the other segment is never aborted, stays locked by the thread that called rollback() and keeps its four stored-fields outputs open, and because the writer is now closed nothing can release them.

## Expected vs actual

- Expected: `rollback()` throws BOOM and the writer ends closed with no open files: both buffered segments are discarded, as with the fix.
- Actual: `MockDirectoryWrapper: cannot close: there are still 4 open files` (`_1.fdt`, `_1.fdm` and two stored-fields temp outputs): the second segment was never aborted.

## Files

- [src/org/apache/lucene/index/TestRollbackExceptionLeaksOtherSegment.java](src/org/apache/lucene/index/TestRollbackExceptionLeaksOtherSegment.java) (JUnit test)

## Run it

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

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestRollbackExceptionLeaksOtherSegment
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestRollbackExceptionLeaksOtherSegment.java](TestRollbackExceptionLeaksOtherSegment.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
