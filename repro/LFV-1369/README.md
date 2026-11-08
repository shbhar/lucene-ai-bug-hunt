# ConcurrentQueryLoader with queueSize &lt; threads never loads any query: each worker's batch size queueSize / threads is 0, so queries added before close() are silently dropped and adding more than queueSize queries blocks forever

Finding LFV-1369 · Area: monitor / ConcurrentQueryLoader (bulk loading of queries into a Monitor) · Priority: low · Impact: silent wrong results · Novelty: new

Finding page: [../../bugs/LFV-1369.html](../../bugs/LFV-1369.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You create new ConcurrentQueryLoader(monitor, 2, 1), add one query and close the loader, the add-then-close pattern of the class javadoc. You expect the query to be registered (monitor.getQueryCount() == 1), as it is with (1, 1) or (2, 2). Instead each of the two workers gets a batch size of queueSize / threads = 1 / 2 = 0, so no worker ever takes anything off the queue: close() returns normally with no error and getQueryCount() is 0 (and with more than queueSize queries add() would block forever).

## Expected vs actual

- Expected: After `close()`, `monitor.getQueryCount()` is 1.
- Actual: `close()` returns normally and `getQueryCount()` is 0: `AssertionError: expected:<1> but was:<0>`.

## Files

- [src/org/apache/lucene/monitor/TestConcurrentQueryLoaderSmallQueue.java](src/org/apache/lucene/monitor/TestConcurrentQueryLoaderSmallQueue.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:$LUCENE/lucene/monitor/build/libs/lucene-monitor-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.monitor.TestConcurrentQueryLoaderSmallQueue
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestConcurrentQueryLoaderSmallQueue.java](TestConcurrentQueryLoaderSmallQueue.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
