# A non-tragic exception during an NRT reopen or commit full flush aborts the DWPTs other threads filled during that flush, silently losing acknowledged documents and updates: the writer stays open without them, or, when the exception is the AlreadyClosedException that close() on another thread gives a reopen in flight, that close() returns normally without the documents

Finding LFV-473 · Area: index / DocumentsWriter full flush (DocumentsWriterFlushControl.abortFullFlushes, blockedFlushes) and the error paths of IndexWriter.getReader and prepareCommit · Priority: medium · Impact: data corruption · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

One thread opens an NRT reader from the IndexWriter (or commits) while other threads keep indexing, and the reopen or commit fails after its flush with an ordinary, non-tragic exception, for example an I/O error while opening a segment file; the writer stays open and the other threads' updateDocument and addDocument calls returned normally with sequence numbers. You expect those changes in the next commit, whose sequence number covers them, and updateDocument to stay atomic. Instead Lucene discards the per-thread buffers that filled up and were parked during the failed full flush, so the added documents never reach the index, and the update keeps only its delete, so the updated document disappears entirely.

## Expected vs actual

- Expected: You expect those changes in the next commit, whose sequence number covers them, and updateDocument to stay atomic.
- Actual: Lucene discards the per-thread buffers that filled up and were parked during the failed full flush, so the added documents never reach the index, and the update keeps only its delete, so the updated document disappears entirely.

## Files

- [src/org/apache/lucene/index/TestGetReaderFailureDropsConcurrentDocs.java](src/org/apache/lucene/index/TestGetReaderFailureDropsConcurrentDocs.java) (JUnit test)

## Run it

- Tested on Lucene main 1a59f96fc6de (a commit of 2026-11-21): still reproduces.
- Tested on Lucene main ea57360912ed (a commit of 2026-10-24): reproduced.
- What this folder covers: the test class holds the three routes with an injected failure (an I/O error while the near-real-time reader opens, an IOException from a merge policy's findFullFlushMerges during a reopen, and an IOException from a merge policy's keepFullyDeletedSegment during a commit) and a control with a successful reopen. The route named in the title, a close() on another thread during a reopen, has no test in this folder. Run on 2026-11-17 on Lucene main 9965d34e3c77 and on 2026-11-18 on Lucene main 1cfa86310f76, with and without assertions: 4 tests found, 3 tests failed (the three routes: the commit after the failure holds 1 of the 3 expected documents) and the control passed.
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
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestGetReaderFailureDropsConcurrentDocs
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

Formal models and scripts this finding's part of the hunt used: [../../models/dw-flush-control-deletequeue-tla/](../../models/dw-flush-control-deletequeue-tla/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
