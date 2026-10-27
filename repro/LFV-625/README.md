# A new IndexWriter cannot open while a non-index file in the index directory is pending deletion, because IndexFileDeleter parses that file's name as a segment name (AssertionError 'wtf? file=...' with -ea; NumberFormatException with -da for names such as app-notes.txt)

Bug LFV-625 · Area: index / IndexFileDeleter start-up (inflateGens) with FSDirectory pending deletions · Priority: low · Impact: crash · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You keep a file of your own in the index directory (or let PersistentSnapshotDeletionPolicy store its snapshots_N files there, which its javadoc allows), delete it through Lucene's FSDirectory while another process still has it open, and then open a new IndexWriter on the same Directory object; you expect the writer to open, because Lucene leaves non-index files alone and since 7.4.0 no longer refuses to start while deletes are pending. Instead the IndexWriter constructor throws: IndexFileDeleter passes every pending deletion, unfiltered, to inflateGens, which treats any name that does not start with segments or pending_segments as a segment file, so with -ea it fails the assertion 'wtf? file=snapshots_0' (or 'wtf? file=app-notes.txt'), and with -da app-notes.txt gives NumberFormatException 'For input string: "pp-notes" under radix 36'. With -da the snapshots_0 case opens, but the writer names its next segment _napshott: the segment counter jumps to 1 + Long.parseLong("napshots", 36), which is cosmetic.

## Expected vs actual

- Expected: You expect the writer to open, because Lucene leaves non-index files alone and since 7.4.0 no longer refuses to start while deletes are pending.
- Actual: The IndexWriter constructor throws: IndexFileDeleter passes every pending deletion, unfiltered, to inflateGens, which treats any name that does not start with segments or pending_segments as a segment file, so with -ea it fails the assertion 'wtf? file=snapshots_0' (or 'wtf? file=app-notes.txt'), and with -da app-notes.txt gives NumberFormatException 'For input string: "pp-notes" under radix 36'.

## Files

- [src/org/apache/lucene/index/TestPendingNonIndexFileBlocksWriterOpen.java](src/org/apache/lucene/index/TestPendingNonIndexFileBlocksWriterOpen.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestPendingNonIndexFileBlocksWriterOpen
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
