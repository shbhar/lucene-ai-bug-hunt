# Monitor HighlightsMatch returns -1 offsets for a TextField document matched in a batch of two or more documents, but the real offsets when the same document is matched alone; the upstream batch test expects the offsets but cannot notice, because Hit.compareTo ignores offsets

Bug LFV-925 · Area: monitor / HighlightsMatch on batches (DocumentBatch: MultiDocumentBatch keeps the caller's FieldType, SingletonDocumentBatch uses MemoryIndex with offsets) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You register a query with a Monitor and match a document whose text is in a plain TextField with HighlightsMatch.MATCHER; each hit carries its positions and character offsets (the phrase "cell biology" in 'the cell biology count' is positions 1-2, offsets 4-16). Match the same document in a batch of two or more documents with Monitor.match(Document) and the hits keep the right positions, but both offsets are -1, because a single document goes into a MemoryIndex that always stores offsets while a batch is indexed with the documents' own field types, and TextField indexes no offsets. The module's own test testHighlightBatches expects the real offsets in a batch (Hit(1, 4, 2, 16)) but cannot notice, because the hit set's contains uses Hit.compareTo, which ignores offsets (the sibling bug LFV-915).

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/monitor/TestMonitorBatchHighlightOffsets.java](src/org/apache/lucene/monitor/TestMonitorBatchHighlightOffsets.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:$LUCENE/lucene/monitor/build/libs/lucene-monitor-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.monitor.TestMonitorBatchHighlightOffsets
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
