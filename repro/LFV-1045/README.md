# updateDocument racing commit can leave two live documents for one key (a lost delete) and operations above the sequence number commit returned in the commit point, because DocumentsWriterFlushControl.nextPendingFlush flushes a DWPT of the new delete queue during the running full flush

Bug LFV-1045 · Area: index / IndexWriter full flush (DocumentsWriterFlushControl.nextPendingFlush) · Priority: medium · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

Several threads replace documents by key with updateDocument while another thread calls commit; you expect every commit point to hold exactly one live document per key and only the operations up to the sequence number commit returned. Instead, an indexing thread that has just seen 'no full flush running' in nextPendingFlush can, if the commit's full flush starts at that moment, flush a buffer that belongs after the commit inside it, so the commit contains newer operations and, when the delete half of an update misses the older document, two live documents for one key. With assertions enabled it often shows instead as a tragic AssertionError in DocumentsWriter.doFlush that closes the writer.

## Expected vs actual

- Expected: You expect every commit point to hold exactly one live document per key and only the operations up to the sequence number commit returned.
- Actual: An indexing thread that has just seen 'no full flush running' in nextPendingFlush can, if the commit's full flush starts at that moment, flush a buffer that belongs after the commit inside it, so the commit contains newer operations and, when the delete half of an update misses the older document, two live documents for one key.

## Files

- [src/org/apache/lucene/index/TestUpdateDocumentConcurrentCommit.java](src/org/apache/lucene/index/TestUpdateDocumentConcurrentCommit.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
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
java -ea -esa -Xmx2g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestUpdateDocumentConcurrentCommit
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/dwfc-nextpendingflush-stale-fullflush-stock-repro/](../../models/dwfc-nextpendingflush-stale-fullflush-stock-repro/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
