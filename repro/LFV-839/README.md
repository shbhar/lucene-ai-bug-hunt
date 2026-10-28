# If Directory.rename(pending_segments_N, segments_N) throws after it took effect, the failed commit leaves the new segments_N in the index (SegmentInfos.finishCommit deletes only pending_segments_N), and IndexWriter then deletes files that this newest segments_N references, so DirectoryReader.open and a new IndexWriter fail until the file is removed by hand (every release since 5.0.0; the rename-branch residual of LUCENE-9477)

Bug LFV-839 · Area: index / commit protocol failure cleanup (SegmentInfos.finishCommit, IndexWriter.finishCommit and rollback) · Priority: low · Impact: index unusable · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You commit through a Directory whose rename(pending_segments_2, segments_2) creates segments_2 and then throws an IOException; commit throws, the writer stays open (the failure is not tragic), and you expect the index to still be the last good commit segments_1, which is what LUCENE-9477 made Lucene do when the directory sync right after the rename fails. Instead SegmentInfos.finishCommit deletes only pending_segments_2, so segments_2 stays in the directory unknown to the writer; as soon as a merge removes the segments it references (a merge that finishes during the commit's fsync, or any merge before the writer is closed or rolled back) the writer deletes their files, and DirectoryReader.open, and after a restart a new IndexWriter, fail with CorruptSegmentInfoException '_1.si cannot be read' although segments_1 and its files are intact. Without a merge, rollback or close deletes the failed commit's files before the stray segments_2 with no directory fsync in between, so a process kill or OS crash in that window leaves the same unopenable index.

## Expected vs actual

- Expected: You expect the index to still be the last good commit segments_1, which is what LUCENE-9477 made Lucene do when the directory sync right after the rename fails.
- Actual: SegmentInfos.finishCommit deletes only pending_segments_2, so segments_2 stays in the directory unknown to the writer; as soon as a merge removes the segments it references (a merge that finishes during the commit's fsync, or any merge before the writer is closed or rolled back) the writer deletes their files, and DirectoryReader.open, and after a restart a new IndexWriter, fail with CorruptSegmentInfoException '_1.si cannot be read' although segments_1 and its files are intact.

## Files

- [src/org/apache/lucene/index/TestCommitRenameFailureOrphanSegmentsFile.java](src/org/apache/lucene/index/TestCommitRenameFailureOrphanSegmentsFile.java) (JUnit test)
- [src/org/apache/lucene/index/TestCommitRenameFailureCrashState.java](src/org/apache/lucene/index/TestCommitRenameFailureCrashState.java) (JUnit test)
- [src2/org/apache/lucene/index/TestCommitRenameFailureOrphanSegmentsFile.java](src2/org/apache/lucene/index/TestCommitRenameFailureOrphanSegmentsFile.java) (JUnit test)
- [src2/org/apache/lucene/index/TestCommitRenameFailureCrashState.java](src2/org/apache/lucene/index/TestCommitRenameFailureCrashState.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
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

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestCommitRenameFailureOrphanSegmentsFile \
       --select-class org.apache.lucene.index.TestCommitRenameFailureCrashState
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestCommitRenameFailureOrphanSegmentsFile \
       --select-class org.apache.lucene.index.TestCommitRenameFailureCrashState
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/failed-commit-then-crash-durability-single-fault/](../../models/failed-commit-then-crash-durability-single-fault/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
