# IndexWriter.getDocStats() under-counts acknowledged documents whose segment is already written but not yet published: possible with out-of-order concurrent flushes since getDocStats was added (7.7.0), and since 9.9.0 (#12647) open during every commit(), flush() or NRT open that writes two or more in-memory segments

Finding LFV-1401 · Area: index / IndexWriter.getDocStats vs. flush-ticket publication (DocumentsWriter.doFlush, DocumentsWriterFlushQueue, IndexWriter.publishFlushedSegment) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1401.html](../../bugs/LFV-1401.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You add two documents from two threads at the same time (so the writer holds two in-memory segments), delete nothing, and call getDocStats() while another thread runs commit(). You expect maxDoc = numDocs = 2 throughout, because DocStats counts every document, flushed or still in the RAM buffer. Instead, while the commit writes the second segment, getDocStats() returns 1 and 1: DocumentsWriter has already removed the first segment's documents from its RAM count, but the full flush adds that segment to SegmentInfos only after its whole loop, so for that time the segment is counted nowhere; the count returns to 2 once the flush publishes.

## Expected vs actual

- Expected: `maxDoc` stays 2 while `commit()` writes the second segment.
- Actual: `AssertionError: expected:<2> but was:<1>`, with assertions on and off; the count is 2 again after the commit.

## Files

- [src/org/apache/lucene/index/TestDocStatsDuringFullFlush.java](src/org/apache/lucene/index/TestDocStatsDuringFullFlush.java) (JUnit test)

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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestDocStatsDuringFullFlush
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestDocStatsDuringFullFlush.java](TestDocStatsDuringFullFlush.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
