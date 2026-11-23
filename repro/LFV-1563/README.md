# With reader pooling off, a merge commit can close IndexWriter with a tragic NoSuchFileException: it releases its merged-away source segments one by one, a release writes that segment's still-pending doc-values update and checkpoints, and the next source's overlay fold then opens an update file that this checkpoint deleted (branch_11x and main with the default overlay limit, 10.6.0 with setMaxDocValuesOverlays set; known upstream, #17048 and #11750)

Finding LFV-1563 · Area: index / IndexWriter merge commit (the release of a finished merge's source segments with reader pooling off; doc-values updates and their overlays) · Priority: low · Impact: crash · Novelty: known upstream

AI-assisted finding, confirmed by automated checks. Upstream status: Fix in progress, by kkewwei (open PR #17053) on 2026-11-20 (https://github.com/apache/lucene/pull/17053). Open PR #17053 (kkewwei, 'Avoid flushing pending DocValues updates to disk for merging segments', opened 2026-11-20, base main, 'Resolved #17048'; no review, no comment and no milestone when read on 2026-11-20) changes IndexWriter.closeMergeReaders, adds a test to TestMergeCarryOverFromDisk and a CHANGES.txt line. It does not cite this list. On a build of the pull request this row's test passes with and without assertions; it fails on main f2b061406c4b and on branch_11x 75eea467ac52, and passes on branch_11x with the pull request's diff applied (run on 2026-11-20). This site files nothing upstream.

## What it shows

You open an IndexWriter with setReaderPooling(false) and otherwise default index settings, add two documents as two segments and call forceMerge(1). While that merge runs, after it has opened its two source segments and before it commits, you update a numeric doc-values field of both documents and call flush(), eight times, then add a third document, update the field once more and call flushNextBuffer(), which flushes the third document's buffer, so that the last update is applied to the two merging segments and not yet written. (The test makes these calls on one thread, from the merge's own OneMerge.wrapForMerge hook under a SerialMergeScheduler, so that their order is fixed; an earlier two-thread test, in which the merge thread commits inside the application thread's ninth flush(), fails with the same exception.) You expect the writer to stay open and all three documents to read 9 after commit(): reader pooling off is a supported setting, and a merge carries the updates that were resolved while it ran over to the merged segment. Instead forceMerge throws java.nio.file.NoSuchFileException for a doc-values update file (_1_8_Lucene90_0.dvm in the runs), and the writer has recorded it as its tragic exception and closed itself: at the merge commit the two source segments are released one after the other, the first release writes that segment's pending update, although the segment has left the index, and checkpoints, the checkpoint deletes update files of the second source that nothing else referenced, and the second source's own write folds its eight earlier update generations and opens one of them by name.

## Expected vs actual

- Expected: You expect the writer to stay open and all three documents to read 9 after commit(): reader pooling off is a supported setting, and a merge carries the updates that were resolved while it ran over to the merged segment.
- Actual: forceMerge throws java.nio.file.NoSuchFileException for a doc-values update file (_1_8_Lucene90_0.dvm in the runs), and the writer has recorded it as its tragic exception and closed itself: at the merge commit the two source segments are released one after the other, the first release writes that segment's pending update, although the segment has left the index, and checkpoints, the checkpoint deletes update files of the second source that nothing else referenced, and the second source's own write folds its eight earlier update generations and opens one of them by name.

## Files

- [src/org/apache/lucene/index/TestDocValuesUpdatesDuringMergeReaderPoolingOff.java](src/org/apache/lucene/index/TestDocValuesUpdatesDuringMergeReaderPoolingOff.java) (JUnit test)

## Run it

- Tested on Lucene main b5a4546a5964 (a commit of 2026-11-22): still reproduces.
- Tested on Lucene main 2cf3f5776d9e (a commit of 2026-11-19): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual, or names the exception that was thrown).
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

# run 1: assertions on, seed C0FFEE900
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=C0FFEE900 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestDocValuesUpdatesDuringMergeReaderPoolingOff
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

Formal models and scripts this finding's part of the hunt used: [../../models/merge-commit-source-release-doc-values-updates/](../../models/merge-commit-source-release-doc-values-updates/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
