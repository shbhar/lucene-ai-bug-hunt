# With a soft-deletes field configured, an IndexWriter opened from a near-real-time reader's IndexCommit (IndexWriterConfig#setIndexCommit) counts the reader's deletions again, so its next commit can drop a segment that still has live documents, write a commit that cannot be opened, or turn a soft delete into a hard delete (since 7.4.0)

Bug LFV-593 · Area: index / IndexWriter construction from a near-real-time reader (ReaderPool pre-enrollment, PendingSoftDeletes reader constructor, soft-deletes accounting) · Priority: medium · Impact: data corruption · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You configure a soft-deletes field, soft-delete one document (for example with an ordinary softUpdateDocument), open a near-real-time reader, close the writer, and open a new IndexWriter from that reader with setIndexCommit(nrtReader.getIndexCommit), as the setIndexCommit javadoc documents. You expect the new writer to start from the reader's point in time and commit every document, with the soft delete still soft. Instead it counts each deletion the reader sees again (a committed soft delete three times, an uncommitted hard delete twice), so its next commit drops a 3-document segment that has 2 live documents, writes a 2-document commit that DirectoryReader.open rejects with 'invalid deletion count: 3 vs maxDoc=2', or hard-deletes the soft-deleted document, and a later forceMerge can silently attach one document's terms to another document's stored fields.

## Expected vs actual

- Expected: You expect the new writer to start from the reader's point in time and commit every document, with the soft delete still soft.
- Actual: It counts each deletion the reader sees again (a committed soft delete three times, an uncommitted hard delete twice), so its next commit drops a 3-document segment that has 2 live documents, writes a 2-document commit that DirectoryReader.open rejects with 'invalid deletion count: 3 vs maxDoc=2', or hard-deletes the soft-deleted document, and a later forceMerge can silently attach one document's terms to another document's stored fields.

## Files

- [src/org/apache/lucene/index/TestWriterFromSoftDeletesNrtReader.java](src/org/apache/lucene/index/TestWriterFromSoftDeletesNrtReader.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestWriterFromSoftDeletesNrtReader
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
