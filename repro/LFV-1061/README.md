# DirectoryReader.open and openIfChanged with an ExecutorService never close the segment readers of tasks the executor already accepted when a later submit is rejected (or when the opening thread is interrupted while it waits), so those segments' files stay open

Bug LFV-1061 · Area: core / index reader open (StandardDirectoryReader.createSegmentReaders, executor branch behind the ExecutorService overloads of DirectoryReader.open and openIfChanged, #15428) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call DirectoryReader.open(directory, executor), or openIfChanged with an executor, and the executor accepts the task for one segment but rejects the submit for the next one, as a saturated bounded pool does; you expect the RejectedExecutionException and nothing left behind, as the method's own cleanup comment intends, as the sequential path does and as upstream's rejecting-executor test requires. Instead the exception leaves the submit loop before any future is collected, so the reader the accepted task built is never decRef'd: on a two-segment index, MockDirectoryWrapper then refuses to close because the files of the accepted segment are still open.

## Expected vs actual

- Expected: You expect the RejectedExecutionException and nothing left behind, as the method's own cleanup comment intends, as the sequential path does and as upstream's rejecting-executor test requires.
- Actual: The exception leaves the submit loop before any future is collected, so the reader the accepted task built is never decRef'd: on a two-segment index, MockDirectoryWrapper then refuses to close because the files of the accepted segment are still open.

## Files

- [src/org/apache/lucene/index/TestExecutorOpenRejectedSubmitLeak.java](src/org/apache/lucene/index/TestExecutorOpenRejectedSubmitLeak.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
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
     execute --select-class org.apache.lucene.index.TestExecutorOpenRejectedSubmitLeak
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/never-judged-backlog-closure-low-and-not-a-bug/](../../models/never-judged-backlog-closure-low-and-not-a-bug/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
