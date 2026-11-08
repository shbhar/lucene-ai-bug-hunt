# IndexRearranger.execute() shuts down its own thread pool only when the run succeeds, so a run that fails after starting work leaves non-daemon 'rearranger' worker threads alive forever and keeps the JVM from exiting

Finding LFV-1080 · Area: misc / index tools (IndexRearranger.execute: lifecycle of the method-local executor on the failure path) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1080.html](../../bugs/LFV-1080.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run IndexRearranger.execute() and it fails after it has started its work, for example because one of your DocumentSelectors throws an IOException. You expect execute() to throw that error and to release the thread pool it created for the run, as it does when the run succeeds. Instead executor.shutdown() is only the last statement of execute(), not in a finally, so on the failure path the pool's worker threads, which are non-daemon and never time out, stay alive forever: a program whose rearrange failed does not exit, and every failed call leaves more idle threads.

## Expected vs actual

- Expected: `execute()` throws `ExecutionException` (cause `IOException: selector failed`) and its pool's worker thread ends.
- Actual: `AssertionError: rearranger-2-thread-1 still alive` 10 s after `execute()` threw, with assertions on and off; 0.28 s pass with the fix.

## Files

- [src/org/apache/lucene/misc/index/TestIndexRearrangerFailureShutsDownPool.java](src/org/apache/lucene/misc/index/TestIndexRearrangerFailureShutsDownPool.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
- Tested on Lucene main e1faeba08bde (2026-11-02): reproduced.
- Recorded outcome of run 1 on Lucene main e1faeba08bde: 1 test(s) run, 1 failed (testFailedExecuteStopsWorker).
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/misc/build/libs/lucene-misc-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.misc.index.TestIndexRearrangerFailureShutsDownPool
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestIndexRearrangerFailureShutsDownPool.java](TestIndexRearrangerFailureShutsDownPool.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
