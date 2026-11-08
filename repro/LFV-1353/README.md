# Multi-threaded CheckIndex.checkIndex() loses an interrupt that arrives after the last segment result: the catch around awaitTermination in its thread-pool shutdown only logs the InterruptedException, so the call returns its Status normally with the thread's interrupt flag cleared (with threadCount 1 the flag is kept)

Finding LFV-1353 · Area: index / CheckIndex concurrent check (internal thread pool shutdown) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1353.html](../../bugs/LFV-1353.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run CheckIndex.checkIndex() with more than one thread (the default on a multi-core machine) and your application interrupts that thread near the end of the check, for example with Future.cancel(true) to stop the job. You expect the interrupt to be reported (Lucene's convention is to throw ThreadInterruptedException) or at least left set, as it is when the same check runs with setThreadCount(1). Instead the finally block that shuts down CheckIndex's internal thread pool catches the InterruptedException from awaitTermination, only writes 'ERROR: Interrupted exception occurred when shutting down executor service' to the info stream, and returns the Status normally, so Thread.interrupted() is false and the cancellation is lost.

## Expected vs actual

- Expected: After `checkIndex()` returns, `Thread.interrupted()` is true (or a `ThreadInterruptedException` was thrown), as with `setThreadCount(1)`.
- Actual: `checkIndex()` returns normally with the flag cleared: `java.lang.AssertionError` at `assertTrue(Thread.interrupted())`, line 49.

## Files

- [src/org/apache/lucene/index/TestCheckIndexKeepsInterrupt.java](src/org/apache/lucene/index/TestCheckIndexKeepsInterrupt.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
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

# run 1: assertions on, seed 23E546C9DD431152
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=3 -Dtests.seed=23E546C9DD431152 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestCheckIndexKeepsInterrupt
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestCheckIndexKeepsInterrupt.java](TestCheckIndexKeepsInterrupt.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
