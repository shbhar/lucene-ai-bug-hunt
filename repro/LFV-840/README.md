# After one failed fsync of its save file, PersistentSnapshotDeletionPolicy's next fault-free snapshot or release fails with FileAlreadyExistsException on its own leftover file: persist leaves the complete snapshots_N behind when Directory.sync throws and does not advance its generation, while snapshot and release undo their change in memory

Bug LFV-840 · Area: index / deletion policies (PersistentSnapshotDeletionPolicy save-file failure handling) · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call PersistentSnapshotDeletionPolicy.snapshot (or release), fsync of the new save file snapshots_N fails once, the call throws and the policy undoes the change in memory; you expect the next call, with no fault, to work. Instead persist has left the complete snapshots_N in the directory (its delete-on-failure block covers only creating and writing the file, not the sync) and has not advanced its generation, so the next snapshot or release writes the same name and fails with FileAlreadyExistsException; that call's cleanup deletes the leftover, and the call after it works. Secondary, contract open: a policy re-opened before that next call adopts the leftover, so a snapshot that threw is in effect and a release that threw has taken effect.

The test shows that after one failed fsync of its save file, the next fault-free PersistentSnapshotDeletionPolicy.snapshot() fails on the save file that persist() left behind.

## Expected vs actual

Expected: the second snapshot() succeeds (the first one was undone in memory); actual: FileAlreadyExistsException: File already exists: snapshots_0.

## Files

- [src/org/apache/lucene/index/TestPersistentSnapshotSyncFailure.java](src/org/apache/lucene/index/TestPersistentSnapshotSyncFailure.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestPersistentSnapshotSyncFailure
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
