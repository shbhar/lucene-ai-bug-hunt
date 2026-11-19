# An IOException late in a doc-values update write leaves IndexWriter open but inconsistent: the failed call does not checkpoint the update files it published, so they can be deleted while still referenced, and after a failed pooled-reader re-open the acknowledged update can be missing from later successful commits and from the index after close(), with no further exception

Finding LFV-110 · Area: index / IndexWriter doc-values update write path (ReadersAndUpdates.writeFieldUpdates, the checkpoint after writeReaderPool, IndexFileDeleter reference counts) · Priority: medium · Impact: data corruption · Novelty: new

Finding page: [../../bugs/LFV-110.html](../../bugs/LFV-110.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You update a doc-values field (updateNumericDocValue, updateBinaryDocValue, or a soft delete) and open an NRT reader, flush or commit, and the doc-values update write throws a non-tragic IOException after the new files were already published into the segment's SegmentCommitInfo (re-opening the writer's pooled segment reader fails, or a second segment's update write in the same batch fails); the writer stays open (isOpen() true, no tragic exception) and you keep using it. You expect the acknowledged update to reach the next NRT reader and commit, and every file the writer's SegmentInfos references to stay on disk, as in 6.x and as the since-deleted test testNoLostDeletesOrUpdates required. Instead the writer skips its IndexFileDeleter checkpoint and keeps a stale pooled reader whose pending update was already pruned: in releases (and on main with maxDocValuesOverlays=0) the next update rewrites the column from that stale reader and a later commit succeeds with the update reverted (doc 0 = 0 instead of 100, no exception), while with overlays (main's default), when the next step is another update or a delete in that segment, or on the partial-batch path, when the next step is a delete or a further doc-values update (in the runs on 10.6.0 with its default configuration, not after every such update), the writer later deletes the published files and NRT opens or commits fail with NoSuchFileException. On main's default config the silent revert happens too when the next step is a merge of that segment, for example forceMerge(1): the merge reads the stale pooled reader, the published overlay files are no longer referenced, and the following commit succeeds with the old value and a clean CheckIndex. No merge call is needed for it: when the application goes on adding documents no later call throws and the commits made after the default merge policy has merged the segment hold the old value, and when it closes the writer without adding a document, close() returns normally and the index has the old value (10.6.0, branch_11x and main).

## Expected vs actual

- Expected: After the failed refresh, the second update and the commit, the reopened index holds `n` = 100 200 2 3.
- Actual: 10.6.0, 10.5.2, 9.12.3: the commit succeeds and holds 0 200 2 3; main and `branch_11x`: the next refresh throws `FileNotFoundException: _0_1_Lucene90_0.dvm`.

## Files

- [src/org/apache/lucene/index/TestDocValuesUpdateReopenFailure.java](src/org/apache/lucene/index/TestDocValuesUpdateReopenFailure.java) (JUnit test)
- [src/org/apache/lucene/index/TestDocValuesUpdateReopenFailureFullRewrite.java](src/org/apache/lucene/index/TestDocValuesUpdateReopenFailureFullRewrite.java) (JUnit test)

## Run it

- Tested on Lucene main 1ae70c6e45b8 (a commit of 2026-11-19): still reproduces.
- Tested on Lucene main ea57360912ed (a commit of 2026-10-24): reproduced.
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
     execute --select-class org.apache.lucene.index.TestDocValuesUpdateReopenFailure

# run 2: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx2g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestDocValuesUpdateReopenFailureFullRewrite
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestDocValuesUpdateReopenFailure.java](TestDocValuesUpdateReopenFailure.java)

Formal models and scripts this finding's part of the hunt used: [../../models/dv-overlays/](../../models/dv-overlays/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
