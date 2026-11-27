# With reader pooling off, a merge commit can close IndexWriter with a tragic NoSuchFileException: it releases its merged-away source segments one by one, a release writes that segment's still-pending doc-values update and checkpoints, and the next source's overlay fold then opens an update file that this checkpoint deleted (branch_11x and main with the default overlay limit until the fix #17053 of 2026-11-25, 10.6.0 with setMaxDocValuesOverlays set; known upstream, #17048 and #11750)

Finding LFV-1563 · Area: index / IndexWriter merge commit (the release of a finished merge's source segments with reader pooling off; doc-values updates and their overlays) · Priority: low · Impact: crash · Novelty: known upstream

AI-assisted finding, confirmed by automated checks. Upstream status: Fixed, by kkewwei (contributor; PR #17053, approved and merged by Ankit Jain (jainankitk) on 2026-11-25; his cherry-pick to branch_11x was committed the same day) on 2026-11-25 (https://github.com/apache/lucene/pull/17053). kkewwei fixed it in PR #17053 (opened 2026-11-20; its description says 'Resolved #17048'): IndexWriter.closeMergeReaders now drops a merged-away source segment from the reader pool before it releases it, so that the release no longer writes that segment's pending doc-values update. Ankit Jain (jainankitk) reviewed the PR on 2026-11-24, approved it on 2026-11-25 and merged it into main the same day as 77d56fefb35; his cherry-pick to branch_11x, 4e8fd3dc3bc, was committed on 2026-11-25 and was on the branch when read on 2026-11-26. Not on branch_10_6 (head 451a69c1d4a, 2026-11-20) when read on 2026-11-26. From our runs of 2026-11-26, each with and without assertions: this row's test passes on main 77d56fefb355 and on branch_11x 4e8fd3dc3bc4 and fails on the commits before them (main 8506598d7a98, branch_11x 55ef56a69344); with IndexWriter.java from before the fix put back on main 77d56fefb355 it fails again. On the 10.6.0 release the test passes as it is (overlays are off by default there) and fails with setMaxDocValuesOverlays(8). This site files nothing upstream.

Fixed upstream: [#17053](https://github.com/apache/lucene/pull/17053) (commit [77d56fefb355](https://github.com/apache/lucene/commit/77d56fefb355652a56d475449268d2ca1844675f)), merged 2026-11-25; also on branch_11x ([4e8fd3dc3bc4](https://github.com/apache/lucene/commit/4e8fd3dc3bc4)).

## What it shows

You open an IndexWriter with setReaderPooling(false) and otherwise default index settings, add two documents as two segments and call forceMerge(1). While that merge runs, after it has opened its two source segments and before it commits, you update a numeric doc-values field of both documents and call flush(), eight times, then add a third document, update the field once more and call flushNextBuffer(), which flushes the third document's buffer, so that the last update is applied to the two merging segments and not yet written. (The test makes these calls on one thread, from the merge's own OneMerge.wrapForMerge hook under a SerialMergeScheduler, so that their order is fixed; an earlier two-thread test, in which the merge thread commits inside the application thread's ninth flush(), fails with the same exception.) You expect the writer to stay open and all three documents to read 9 after commit(): reader pooling off is a supported setting, and a merge carries the updates that were resolved while it ran over to the merged segment. Instead forceMerge throws java.nio.file.NoSuchFileException for a doc-values update file (_1_8_Lucene90_0.dvm in the runs), and the writer has recorded it as its tragic exception and closed itself: at the merge commit the two source segments are released one after the other, the first release writes that segment's pending update, although the segment has left the index, and checkpoints, the checkpoint deletes update files of the second source that nothing else referenced, and the second source's own write folds its eight earlier update generations and opens one of them by name.

## Expected vs actual

- Expected: You expect the writer to stay open and all three documents to read 9 after commit(): reader pooling off is a supported setting, and a merge carries the updates that were resolved while it ran over to the merged segment.
- Actual: forceMerge throws java.nio.file.NoSuchFileException for a doc-values update file (_1_8_Lucene90_0.dvm in the runs), and the writer has recorded it as its tragic exception and closed itself: at the merge commit the two source segments are released one after the other, the first release writes that segment's pending update, although the segment has left the index, and checkpoints, the checkpoint deletes update files of the second source that nothing else referenced, and the second source's own write folds its eight earlier update generations and opens one of them by name.

## Files

- [src/org/apache/lucene/index/TestDocValuesUpdatesDuringMergeReaderPoolingOff.java](src/org/apache/lucene/index/TestDocValuesUpdatesDuringMergeReaderPoolingOff.java) (JUnit test)

## Run it

- Tested on Lucene main 2cf3f5776d9e (a commit of 2026-11-19): reproduced.
- Upstream fixed this finding: #17053 was merged into main on 2026-11-25 as 77d56fefb355, and its cherry-pick to branch_11x, 4e8fd3dc3bc4, was committed the same day (see Fixed upstream above). On builds with the fix the test passes: run on a build of main 77d56fefb355 (V as printed) and of branch_11x 4e8fd3dc3bc4 (set V=11.0.0-SNAPSHOT), the class reports 1 test found and 1 successful. To see the finding, run the commands on a build without the fix: on builds of main 8506598d7a98 (V as printed) and of branch_11x 55ef56a69344 (V=11.0.0-SNAPSHOT), the commits before the fix and before the cherry-pick, the class reports 1 test found and 1 failed, with 'java.nio.file.NoSuchFileException: _1_8_Lucene90_0.dvm'. The 10.6.0 release needs one more call: with lucene-core-10.6.0.jar, lucene-codecs-10.6.0.jar and lucene-test-framework-10.6.0.jar (Maven Central, org.apache.lucene) in place of the three $LUCENE jars and V=10.6.0 the test passes as it is, because overlays are off by default in that release; with .setMaxDocValuesOverlays(8) added to the test's IndexWriterConfig, after .setReaderPooling(false), it fails with the same exception. Each of these runs, made on 2026-11-26, gave the same result with and without assertions.
- This finding was re-run on Lucene main 82cf7465bb8f on 2026-11-26: it no longer reproduces there.
- How to read the outcome: a failing test is the finding reproducing: the test ends with java.nio.file.NoSuchFileException for a doc-values update file (_1_8_Lucene90_0.dvm in the runs named above); on the builds with the fix that the note names, the test passes.
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

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on branch_11x the build takes the default from the seed).

Formal models and scripts this finding's part of the hunt used: [../../models/merge-commit-source-release-doc-values-updates/](../../models/merge-commit-source-release-doc-values-updates/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
