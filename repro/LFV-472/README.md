# Merge-on-refresh never releases the file references of a merged segment that is dropped because all its documents were deleted during the merge, so IndexWriter leaves that segment's files on disk until the next writer opens the index

Bug LFV-472 · Area: index / IndexWriter NRT merge-on-refresh (getReader point-in-time merges) and IndexFileDeleter refcounts · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You keep an IndexWriter open, refresh near-real-time readers from it (DirectoryReader.openIfChanged) with merge-on-refresh on, which is the default since 9.3, and while a refresh-triggered merge of small segments runs, a commit or flush applies deletes that remove every document of that merge. You expect the merged segment, which IndexWriter drops as 100% deleted, to have its files deleted, as happens when the same segment is dropped by an ordinary merge. Instead the refresh has already taken a file reference on it and later closes its reader without releasing that reference, so the segment's files stay in the directory through deleteUnusedFiles, commit, close and rollback, until the next IndexWriter opens the index.

The test shows that a merge-on-refresh (GET_READER) merge whose documents are all deleted while it runs is dropped, and its file references are never released, so IndexWriter leaves the merged segment's files on disk after close().

## Expected vs actual

Expected: no files outside the last commit after `DirectoryReader.open(w).close(); w.close()`. Actual: `expected:<[]> but was:<[_2.fdm, _2.fdt, _2.fdx, _2.fnm, _2.si, _2_Lucene104_0.doc,...]>`.

## Files

- [src/org/apache/lucene/index/TestGetReaderMergeDroppedSegmentLeak.java](src/org/apache/lucene/index/TestGetReaderMergeDroppedSegmentLeak.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestGetReaderMergeDroppedSegmentLeak
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/indexfiledeleter-refcount-tla/](../../models/indexfiledeleter-refcount-tla/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
