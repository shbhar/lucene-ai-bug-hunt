# If anything throws in IndexWriter.finishCommit after segments_N was renamed into place but before the commit checkpoint took the new commit's file references (an InfoStream exception, or an OutOfMemoryError at that point), the writer closes as tragic but its finalizer first releases those references, so files that only the new commit uses are deleted and the newest durable commit, and with it DirectoryReader.open and a new IndexWriter, fails until that segments_N is removed by hand (LUCENE-8217 dropped the LUCENE-5958 guard; every release since 7.4.0)

Finding LFV-1410 · Area: index / IndexWriter commit failure handling (finishCommit reference release, IndexFileDeleter.checkpoint) · Priority: medium · Impact: index unusable · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call prepareCommit(), let a merge (here forceMerge(1)) retire the segments of the prepared commit, and then call commit(); right after commit() has renamed pending_segments_2 to segments_2, your InfoStream's next log call throws (an OutOfMemoryError at the same point does the same). IndexWriter correctly treats this as a tragedy and closes itself, and you expect the finished commit segments_2 to stay readable, as it does on 7.3.1. Instead finishCommit's try-with-resources finalizer releases the commit's file references before the commit checkpoint has taken its own, so the merged-away segment's files are deleted and DirectoryReader.open(dir) fails with CorruptSegmentInfoException '_1.si cannot be read' until segments_2 is deleted by hand.

## Expected vs actual

- Expected: You expect the finished commit segments_2 to stay readable, as it does on 7.3.1.
- Actual: finishCommit's try-with-resources finalizer releases the commit's file references before the commit checkpoint has taken its own, so the merged-away segment's files are deleted and DirectoryReader.open(dir) fails with CorruptSegmentInfoException '_1.si cannot be read' until segments_2 is deleted by hand.

## Files

- [src/org/apache/lucene/index/TestCommitFailureAfterRename.java](src/org/apache/lucene/index/TestCommitFailureAfterRename.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): reproduced.
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

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestCommitFailureAfterRename
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
