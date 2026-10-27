# A Directory.deleteFile failure during a non-commit IndexFileDeleter checkpoint (for example while a flush drops a fully deleted segment) leaves one extra reference on every file of the writer's current segments, so the still-open IndexWriter never deletes those files once they become unused, not even in deleteUnusedFiles, rollback or close

Bug LFV-630 · Area: core / index IndexFileDeleter reference counting (non-commit checkpoint after a failed Directory.deleteFile; reached from IndexWriter flush, NRT flush and tryDeleteDocument) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index with an IndexWriter on a Directory whose deleteFile can throw an IOException (Directory.deleteFile declares it), and one delete fails while the writer checkpoints outside a commit, for example when flush drops a newly flushed segment whose documents were all deleted; flush throws, but the writer stays open and reports no tragic exception, so you keep indexing, merging and committing. You expect the writer to delete the files of segments that are merged away once no commit references them, as IndexFileDeleter's reference counting promises (LUCENE-5919 made its decRef exception-safe for this reason). Instead the checkpoint has already taken a reference on every current file and, because decRef threw, skips the line that records those files for release, so in the repro _0.cfe, _0.cfs and _0.si stay on disk after the merge, the next commit, deleteUnusedFiles and close (rollback), and only a new IndexWriter removes them.

## Expected vs actual

- Expected: You expect the writer to delete the files of segments that are merged away once no commit references them, as IndexFileDeleter's reference counting promises (LUCENE-5919 made its decRef exception-safe for this reason).
- Actual: The checkpoint has already taken a reference on every current file and, because decRef threw, skips the line that records those files for release, so in the repro _0.cfe, _0.cfs and _0.si stay on disk after the merge, the next commit, deleteUnusedFiles and close (rollback), and only a new IndexWriter removes them.

## Files

- [src/org/apache/lucene/index/TestCheckpointDecRefFailureLeaksFiles.java](src/org/apache/lucene/index/TestCheckpointDecRefFailureLeaksFiles.java) (JUnit test)

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

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestCheckpointDecRefFailureLeaksFiles
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
