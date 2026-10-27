# An IOException late in a doc-values update write leaves IndexWriter open but inconsistent: published update files are never checkpointed and get deleted while still referenced, and a failed pooled-reader re-open silently drops the update

Bug LFV-110 · Area: index / IndexWriter doc-values update write path (ReadersAndUpdates.writeFieldUpdates, the checkpoint after writeReaderPool, IndexFileDeleter reference counts) · Priority: medium · Impact: data corruption · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You update a doc-values field (updateNumericDocValue, updateBinaryDocValue, or a soft delete) and open an NRT reader, flush or commit, and the doc-values update write throws a non-tragic IOException after the new files were already published into the segment's SegmentCommitInfo (re-opening the writer's pooled segment reader fails, or a second segment's update write in the same batch fails); the writer stays open (isOpen true, no tragic exception) and you keep using it. You expect the acknowledged update to reach the next NRT reader and commit, and every file the writer's SegmentInfos references to stay on disk, as in 6.x and as the since-deleted test testNoLostDeletesOrUpdates required. Instead the writer skips its IndexFileDeleter checkpoint and keeps a stale pooled reader whose pending update was already pruned: in releases (and on main with maxDocValuesOverlays=0) the next update rewrites the column from that stale reader and a later commit succeeds with the update reverted (doc 0 = 0 instead of 100, no exception), while with overlays (main's default) or on the partial-batch path the writer later deletes the published files and NRT opens or commits fail with NoSuchFileException.

## Expected vs actual

- Expected: You expect the acknowledged update to reach the next NRT reader and commit, and every file the writer's SegmentInfos references to stay on disk, as in 6.x and as the since-deleted test testNoLostDeletesOrUpdates required.
- Actual: The writer skips its IndexFileDeleter checkpoint and keeps a stale pooled reader whose pending update was already pruned: in releases (and on main with maxDocValuesOverlays=0) the next update rewrites the column from that stale reader and a later commit succeeds with the update reverted (doc 0 = 0 instead of 100, no exception), while with overlays (main's default) or on the partial-batch path the writer later deletes the published files and NRT opens or commits fail with NoSuchFileException.

## Files

- [src/org/apache/lucene/index/TestDocValuesUpdateReopenFailure.java](src/org/apache/lucene/index/TestDocValuesUpdateReopenFailure.java) (JUnit test)
- [src/org/apache/lucene/index/TestDocValuesUpdateReopenFailureFullRewrite.java](src/org/apache/lucene/index/TestDocValuesUpdateReopenFailureFullRewrite.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
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
     execute --select-class org.apache.lucene.index.TestDocValuesUpdateReopenFailure

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestDocValuesUpdateReopenFailureFullRewrite
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
