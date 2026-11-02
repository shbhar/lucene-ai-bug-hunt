# If IndexWriter's InfoStream throws on the 'finishFullFlush' log line of a commit, flush or NRT open, the still-open writer never leaves full-flush mode, so it stops flushing in-memory segments (maxBufferedDocs is ignored) and, with RAM-based flushing, a lone indexing thread later blocks forever in addDocument (every release since 4.0.0)

Finding LFV-1084 · Area: index / IndexWriter full flush (DocumentsWriter.finishFullFlush, DocumentsWriterFlushControl blocked flushes and stall control) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1084.html](../../bugs/LFV-1084.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You enable an InfoStream on an IndexWriter, and the logging sink throws a RuntimeException once, on the message DocumentsWriter.finishFullFlush logs at the end of a commit (prepareCommit, flush() and NRT opens take the same path). You expect commit() to fail with that exception and the writer, which stays open and has no tragic exception, to keep honouring its flush settings ('the writer will flush every maxBufferedDocs added documents'). Instead the log call runs before the code that ends the full flush, so the writer stays in full-flush mode: every in-memory segment that should flush is parked as blocked (10 more adds with maxBufferedDocs=2 leave 10 documents buffered), and with the default RAM-based flushing a thread that keeps indexing without another commit or refresh eventually waits forever in addDocument.

## Expected vs actual

- Expected: `commit()` throws, the writer stays open, and after 10 more documents at most 1 is left in memory (`numRamDocs() < 2`).
- Actual: `numRamDocs()` is 10: nothing was flushed, with assertions on and off (`AssertionError: numRamDocs=10`).

## Files

- [src/org/apache/lucene/index/TestCommitLogFailureStopsFlushing.java](src/org/apache/lucene/index/TestCommitLogFailureStopsFlushing.java) (JUnit test)

## Run it

- Tested on Lucene main e1faeba08bde (2026-11-02): still reproduces.
- Recorded outcome of run 1 on Lucene main e1faeba08bde: 1 test(s) run, 1 failed (testFlushesAfterCommitFailedOnLogMessage).
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

# run 1: assertions on, seed 5EEDF1F0
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=5EEDF1F0 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestCommitLogFailureStopsFlushing
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestCommitLogFailureStopsFlushing.java](TestCommitLogFailureStopsFlushing.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
