# PersistentSnapshotDeletionPolicy adopts a broken newest save file on load and deletes the good older one, so an OS crash, power loss or process kill while snapshot or release writes its save file silently drops every earlier persisted snapshot, and IndexWriter then deletes the snapshotted commits

Bug LFV-199 · Area: index / deletion policies (PersistentSnapshotDeletionPolicy save-file loading, crash recovery) · Priority: medium · Impact: data corruption · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You use PersistentSnapshotDeletionPolicy to protect a commit (for example for a backup): snapshot returns, which its javadoc says means the snapshot is persisted, and later the machine crashes, loses power or the process is killed while another snapshot or release call is writing the next save file snapshots_N. You expect that after a restart the policy still holds the earlier snapshot (persist keeps the previous save file until the new one is fsynced, so there is a good file to fall back on). Instead loadPriorSnapshots takes its state from the newest, empty or truncated save file even though reading it threw, deletes the good older save file, and reports no snapshots, with no exception or log, so the next IndexWriter deletes the snapshotted commit (in the repro: snapshot count 0, segments_1 deleted, only commit 2 left).

## Expected vs actual

- Expected: You expect that after a restart the policy still holds the earlier snapshot (persist keeps the previous save file until the new one is fsynced, so there is a good file to fall back on).
- Actual: loadPriorSnapshots takes its state from the newest, empty or truncated save file even though reading it threw, deletes the good older save file, and reports no snapshots, with no exception or log, so the next IndexWriter deletes the snapshotted commit (in the repro: snapshot count 0, segments_1 deleted, only commit 2 left).

## Files

- [src/org/apache/lucene/index/TestPersistentSnapshotBrokenSaveFile.java](src/org/apache/lucene/index/TestPersistentSnapshotBrokenSaveFile.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestPersistentSnapshotBrokenSaveFile
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
