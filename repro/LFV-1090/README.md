# After addDocument fails with a non-tragic exception while an in-memory segment copies its backlog of buffered deletes and doc-values updates, the segment's next document resets its delete slice past the operations that were never applied, so IndexWriter stays open and the next successful commit silently lacks an acknowledged delete of a buffered document

Finding LFV-1090 · Area: index / IndexWriter delete queue (DocumentsWriterPerThread.finishDocuments, DeleteSlice failure handling) · Priority: medium · Impact: data corruption · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You add a document to an IndexWriter, then send a long run of doc-values updates (executed: 2.2 million updateNumericDocValue calls selected by one 1 KB term) and delete the buffered document; the next addDocument fails with ArithmeticException 'integer overflow' while the in-memory segment copies that backlog into its own buffer, and the writer stays open with no tragic exception. You expect either the writer to close itself or the next successful commit, whose sequence number is above the delete's, to reflect the delete. Instead the following addDocument finds no new deletes and resets the segment's delete slice past the operations the failed copy never reached, so the commit succeeds and the deleted document is still in the index.

## Expected vs actual

- Expected: The writer has closed itself after the failed `addDocument`, or the next commit has 0 hits for `id:buffered`.
- Actual: `AssertionError: expected:<0> but was:<1>`: the commit (sequence number 2,200,005) still holds `buffered`, assertions on and off.

## Files

- [src/org/apache/lucene/index/TestDeleteLostAfterFailedAddDocument.java](src/org/apache/lucene/index/TestDeleteLostAfterFailedAddDocument.java) (JUnit test)

## Run it

- Tested on Lucene main e1faeba08bde (2026-11-02): reproduced.
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
java -ea -esa -Xmx3g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestDeleteLostAfterFailedAddDocument
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this finding's part of the hunt used: [../../models/nontragic-indexing-failure-loses-acknowledged-ops/](../../models/nontragic-indexing-failure-loses-acknowledged-ops/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
