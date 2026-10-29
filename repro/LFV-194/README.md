# PersistentSnapshotDeletionPolicy.snapshot and release return before the new save file's name is durable: persist fsyncs snapshots_N but never calls Directory.syncMetaData, so an OS crash or power loss can lose an acknowledged snapshot

Bug LFV-194 · Area: index / deletion policies (PersistentSnapshotDeletionPolicy persistence, directory fsync) · Priority: low · Impact: data corruption · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You protect a commit with PersistentSnapshotDeletionPolicy.snapshot, whose javadoc says that once the method returns the snapshot information is persisted in the directory. Instead persist writes and fsyncs the new snapshots_N file but never fsyncs the directory (Directory.syncMetaData), the step Lucene's own commit takes because a new file's name is not durable until then. After an OS crash or power loss the save file can be gone: the snapshot count comes back as 0 and, with the snapshots in a separate Directory, the reopened IndexWriter deletes the commit the snapshot was protecting (a lost release instead brings a released snapshot back).

The test shows that `PersistentSnapshotDeletionPolicy.snapshot()`, documented as "once this method returns, the snapshot information is persisted", syncs the bytes of the new `snapshots_N` file but never calls `Directory.syncMetaData()`, so the new file's name is not made durable.

## Expected vs actual

Expected at least one `syncMetaData()` call on the snapshot directory during `snapshot()`; actual: 0 (`expected >= 1 syncMetaData() call in snapshot(), got 0`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/index/TestPersistentSnapshotSyncMetaData.java](src/org/apache/lucene/index/TestPersistentSnapshotSyncMetaData.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestPersistentSnapshotSyncMetaData
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
