# An exception inside DocumentsWriterPerThread.prepareFlush is not tragic: IndexWriter stays open but drops the in-memory segment and the deletes and doc-values updates it had just frozen, so the next successful commit lacks acknowledged operations its sequence number covers

Finding LFV-1078 · Area: index / DocumentsWriter flush failure handling (DocumentsWriter.doFlush, DocumentsWriterPerThread.prepareFlush, DocumentsWriterDeleteQueue.freezeGlobalBuffer) · Priority: medium · Impact: data corruption · Novelty: new

Finding page: [../../bugs/LFV-1078.html](../../bugs/LFV-1078.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You buffer a document, a delete and doc-values updates in an IndexWriter, and commit() fails with an ordinary exception while the in-memory segment is being prepared for flushing (executed: ArithmeticException 'integer overflow' after about 2.2 million updateNumericDocValue calls on a 1 KB id); the writer stays open and reports no tragic exception. You expect either the writer to close itself, as it does for every other failure while flushing a segment, or the next successful commit (or close()) to contain every operation its returned sequence number covers. Instead the failed flush has already taken the buffered deletes and updates out of the shared delete queue and then drops them together with the in-memory segment, so the next commit silently lacks the buffered document, keeps the deleted document live and holds a stale doc-values value.

## Expected vs actual

- Expected: After the failed `commit()` the writer has closed itself, or the next commit has `buffered=1 victim=0`.
- Actual: `ComparisonFailure: expected:<buffered=[1 victim=0]> but was:<buffered=[0 victim=1]>`, with assertions on and off.

## Files

- [src/org/apache/lucene/index/TestFailedPrepareFlushLosesOps.java](src/org/apache/lucene/index/TestFailedPrepareFlushLosesOps.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
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

# run 1: assertions off (production-like), seed 1A2B3C4D
java -da -dsa -Dtests.asserts=false -Xmx3g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.monster=true -Dtests.seed=1A2B3C4D \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestFailedPrepareFlushLosesOps
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestFailedPrepareFlushLosesOps.java](TestFailedPrepareFlushLosesOps.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
