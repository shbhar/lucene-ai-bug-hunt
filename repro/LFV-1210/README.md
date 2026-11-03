# ConcurrentMergeScheduler.updateMergeThreads throws NullPointerException when merge logging is switched on during the call, because it allocates its log buffer only if its first verbose() check is true and then checks verbose() again before using the buffer

Finding LFV-1210 · Area: index / ConcurrentMergeScheduler (updateMergeThreads infoStream logging; IO-throttle setters, merge start and finish) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1210.html](../../bugs/LFV-1210.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You give IndexWriterConfig an InfoStream that follows a logger level, and you switch merge-scheduler logging on while the writer runs, at a moment when ConcurrentMergeScheduler.updateMergeThreads is running (it runs in setForceMergeMBPerSec, enableAutoIOThrottle and disableAutoIOThrottle, and at every merge start and finish). You expect the call to finish normally and logging to start, as it does with a constant InfoStream and as it did in 4.x. Instead the method, which skipped creating its log buffer because its first verbose() check said false, sees true at a later check and throws NullPointerException ('Cannot invoke "java.lang.StringBuilder.toString()" because "message" is null').

## Expected vs actual

- Expected: `setForceMergeMBPerSec(10.0)` stores 10.0 MB/s and returns normally; logging is on from then on.
- Actual: `NullPointerException: Cannot invoke "java.lang.StringBuilder.toString()" because "message" is null` at `updateMergeThreads(ConcurrentMergeScheduler.java:438)`, with assertions on and off.

## Files

- [src/org/apache/lucene/index/TestConcurrentMergeSchedulerVerboseTurnsOn.java](src/org/apache/lucene/index/TestConcurrentMergeSchedulerVerboseTurnsOn.java) (JUnit test)

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
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestConcurrentMergeSchedulerVerboseTurnsOn
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestConcurrentMergeSchedulerVerboseTurnsOn.java](TestConcurrentMergeSchedulerVerboseTurnsOn.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
