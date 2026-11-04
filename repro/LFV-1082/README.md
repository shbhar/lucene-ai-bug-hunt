# If the IndexWriter InfoStream throws a RuntimeException while a flushed segment is being published, IndexWriter drops that already written segment (and, for some log lines, the deletes and doc-values updates on its flush ticket) but stays open, so the next successful commit silently lacks acknowledged documents its sequence number covers (every release since 4.0.0)

Finding LFV-1082 · Area: index / IndexWriter flush publishing (IndexWriter.publishFlushedSegments, IndexWriter.publishFlushedSegment, DocumentsWriterFlushQueue.innerPurge) · Priority: medium · Impact: data corruption · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You enable IndexWriter's InfoStream, add a document and commit, and the logging call for one of the 'publish' lines of that commit throws an unchecked exception (executed: a java.util.logging Handler behind Lucene's JavaLoggingInfoStream that fails once); commit() throws, and the writer stays open with no tragic exception. You expect either the writer to close itself, as it does when the segment flush itself fails or a published delete packet fails to apply, or the next successful commit to contain every operation its returned sequence number covers. Instead the segment was already fully written and its flush ticket is removed from the queue whether or not it was published, so the segment never joins the index: the retried commit returns seqNo 5, above the addDocument's seqNo 2, yet the committed index has 0 documents.

## Expected vs actual

- Expected: After the failed `commit()` the writer has closed itself, or the next commit has 1 document.
- Actual: `AssertionError: expected:<1> but was:<0>`: the writer stays open and the retried commit has 0 documents, assertions on and off.

## Files

- [src/org/apache/lucene/index/TestFailedSegmentPublishDropsDocument.java](src/org/apache/lucene/index/TestFailedSegmentPublishDropsDocument.java) (JUnit test)

## Run it

- Tested on Lucene main e1faeba08bde (2026-11-02): still reproduces.
- Recorded outcome of run 1 on Lucene main e1faeba08bde: 1 test(s) run, 1 failed (testCommitAfterFailedPublishKeepsDocument).
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

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestFailedSegmentPublishDropsDocument
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
