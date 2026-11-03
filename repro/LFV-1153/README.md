# After an IndexWriter has started using a ConcurrentMergeScheduler, setMaxMergesAndThreads(AUTO_DETECT_MERGES_AND_THREADS, AUTO_DETECT_MERGES_AND_THREADS) leaves both limits at -1, so the next commit, close or forceMerge that registers a merge waits in maybeStall forever and no merge thread is ever started

Finding LFV-1153 · Area: index / ConcurrentMergeScheduler merge limits (setMaxMergesAndThreads reset to AUTO_DETECT_MERGES_AND_THREADS, initialize, maybeStall) · Priority: low · Impact: hang · Novelty: new

Finding page: [../../bugs/LFV-1153.html](../../bugs/LFV-1153.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You reset the merge limits of the ConcurrentMergeScheduler that an open IndexWriter uses to the documented automatic default with setMaxMergesAndThreads(AUTO_DETECT_MERGES_AND_THREADS, AUTO_DETECT_MERGES_AND_THREADS), a pair the setter accepts. You expect the scheduler to go back to its CPU-based defaults, as it did up to 8.5.2. Instead both limits stay at -1, because only initialize() (run once, when the writer is opened) resolves AUTO, so the next commit, close or forceMerge that registers a merge waits in maybeStall forever, with no error and no merge thread started, until concrete limits are set again (a rollback() from another thread freed a stalled forceMerge, but by reading it cannot free a stalled commit on the merge-on-commit path or a stalled close()).

## Expected vs actual

- Expected: `forceMerge(1)` merges the two segments and returns; with the fix the test method takes 0.14 s.
- Actual: `AssertionError: forceMerge(1) hangs after resetting a live ConcurrentMergeScheduler to AUTO_DETECT` after 10 s, with assertions on and off.

## Files

- [src/org/apache/lucene/index/TestCMSLiveResetToAutoDetect.java](src/org/apache/lucene/index/TestCMSLiveResetToAutoDetect.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestCMSLiveResetToAutoDetect
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestCMSLiveResetToAutoDetect.java](TestCMSLiveResetToAutoDetect.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
