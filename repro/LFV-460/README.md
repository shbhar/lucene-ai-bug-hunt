# IndexWriter#hasDeletions ignores committed soft deletes in segments the writer has not pooled, so a writer reopened on an index whose only deletions are soft deletes (or one that took such segments through addIndexes(Directory)) answers false, and true once an NRT reader is opened

Bug LFV-460 · Area: index / IndexWriter statistics (soft deletes) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You configure a soft-deletes field, soft-delete a document (softUpdateDocument, or updateDocValues on that field), commit, close, and open a new IndexWriter on the index; you expect hasDeletions to return true, because its javadoc says 'Returns true if this index has deletions', IndexWriterConfig#setSoftDeletesField says a soft-deleted document 'is treated as if it has been hard-deleted through the IndexWriter API', and the same writer's getDocStats reports maxDoc 3 and numDocs 2. Instead it returns false, with -ea and -da, until DirectoryReader.open(writer) puts the segment into the reader pool; after that the same call on the unchanged index returns true. The loop over committed segments uses SegmentCommitInfo#hasDeletions (delGen != -1, hard deletes only), while the buffered and pooled branches of the same method count soft deletes; the one-line fix is info.getDelCount(softDeletesEnabled) &gt; 0.

The test shows that with a soft-deletes field, a document soft-deleted by `softUpdateDocument` and committed makes a newly opened `IndexWriter` answer `hasDeletions() == false`, while the same writer's `getDocStats()` counts the deleted document.

## Expected vs actual

Expected `true` (getDocStats maxDoc=2, numDocs=1); actual `false` (`expected:<true> but was:<false>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/index/TestIndexWriterHasDeletionsSoftDeletes.java](src/org/apache/lucene/index/TestIndexWriterHasDeletionsSoftDeletes.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
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
     execute --select-class org.apache.lucene.index.TestIndexWriterHasDeletionsSoftDeletes
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
