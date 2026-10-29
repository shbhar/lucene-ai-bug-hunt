# A PersistentSnapshotDeletionPolicy re-opened over its save file counts the persisted snapshots, but getSnapshots is empty and getIndexCommit(gen) returns null until an IndexWriter has opened with it, and no javadoc states this precondition (documentation gap; the behaviour is by design)

Bug LFV-890 · Area: index / deletion policies (SnapshotDeletionPolicy and PersistentSnapshotDeletionPolicy restart lifecycle, javadoc) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You restart an application that uses PersistentSnapshotDeletionPolicy, create the policy again over its save file with OpenMode.APPEND (whose javadoc says this opens the existing snapshots, 'initializing the class with the snapshots information'), and ask it which commits are snapshotted before you open an IndexWriter with it. You expect getSnapshots and getIndexCommit(gen) to show the persisted snapshot that getSnapshotCount counts, or the javadoc to say that the policy must first be passed to an IndexWriter. Instead getSnapshotCount is 1 while getSnapshots is empty and getIndexCommit(gen) returns null, which its javadoc says means 'not currently snapshotted'; this is by design (the IndexCommit objects come from the writer's onInit call), but no javadoc states the precondition.

The test shows that a PersistentSnapshotDeletionPolicy re-opened over its save file (OpenMode.APPEND) counts the persisted snapshot but getIndexCommit(gen) returns null until an IndexWriter has opened with it, which no javadoc states (TestReopenedPersistentSnapshotGetIndexCommit#testGetIndexCommitBeforeWriterOpens; documentation gap, behaviour by design).

## Expected vs actual

Expected the snapshotted IndexCommit (javadoc: null only if "not currently snapshotted"); actual null while getSnapshotCount() is 1, with -ea and -da.

## Files

- [src/org/apache/lucene/index/TestReopenedPersistentSnapshotGetIndexCommit.java](src/org/apache/lucene/index/TestReopenedPersistentSnapshotGetIndexCommit.java) (JUnit test)

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

# run 1: assertions on, seed 5EED0890
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=5EED0890 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestReopenedPersistentSnapshotGetIndexCommit
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/snapshot-deletion-policies-commit-retention-model-based/](../../models/snapshot-deletion-policies-commit-retention-model-based/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
