# A failed addIndexes(CodecReader...) merge leaves its partially written segment files on disk while the writer stays open: commit(), deleteUnusedFiles(), later merges and a successful retry never delete them, only rollback(), close() or a new IndexWriter does (regression from LUCENE-6786, every release since 5.4.0)

Finding LFV-1405 · Area: index / IndexWriter addIndexes(CodecReader...) failure cleanup (addIndexesReaderMerge, IndexFileDeleter) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call IndexWriter.addIndexes(CodecReader...) and the merge it runs fails partway, for example because the destination Directory throws an IOException (disk full); the call throws, the writer stays open and is not tragic, and you expect the merge's partially written files to be deleted, as the same method does for its compound-file and .si stages and as Lucene did before 5.4.0. Instead those files were never handed to IndexFileDeleter, so commit(), deleteUnusedFiles(), later merges and even a successful retry of the same call leave them on disk. Only rollback(), close() or opening a new IndexWriter, which re-list the directory, delete them.

## Expected vs actual

- Expected: You expect the merge's partially written files to be deleted, as the same method does for its compound-file and .si stages and as Lucene did before 5.4.0.
- Actual: Those files were never handed to IndexFileDeleter, so commit(), deleteUnusedFiles(), later merges and even a successful retry of the same call leave them on disk.

## Files

- [src/org/apache/lucene/index/TestAddIndexesFailedMergeFiles.java](src/org/apache/lucene/index/TestAddIndexesFailedMergeFiles.java) (JUnit test)

## Run it

- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestAddIndexesFailedMergeFiles
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
