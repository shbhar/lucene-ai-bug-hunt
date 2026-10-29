# When opening a persistent Monitor fails after its query index has built the IndexWriter (stored queries the serializer cannot parse, no serializer for a non-empty index, an I/O error, or a purge frequency of 0), the writer and its write lock are never released, so the same index cannot be opened for writing again in that JVM

Bug LFV-507 · Area: monitor / persistent query index (WritableQueryIndex construction, failure cleanup) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You open a persistent Monitor (MonitorConfiguration.setIndexPath) and the open fails after the query index has built its IndexWriter, for example because a stored query no longer parses with the current parser (IllegalStateException 'Couldn't parse some queries from the index'), because no serializer was given for a non-empty index, or because of an I/O error while the index opens. You expect the failed constructor to release what it acquired, as IndexWriter's own constructor does for its write lock, so that you can fix the parser or the configuration and open the index again. Instead the IndexWriter, its SearcherManager and the write lock stay open with no object left to close them, so every later writable Monitor on that path in the same JVM fails with LockObtainFailedException 'Lock held by this virtual machine', even with a correct serializer, until the JVM restarts.

The test shows that when opening a persistent Monitor fails because the parser rejects a stored query, the IndexWriter it built keeps the index's write lock, so a retry with the working serializer cannot open the index in the same JVM.

## Expected vs actual

Expected the retry to open the index with 1 query; actual `LockObtainFailedException: Lock held by this virtual machine` (plus the test framework's LeakFS report for the same write.lock channel), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/monitor/TestMonitorReopenAfterFailedOpen.java](src/org/apache/lucene/monitor/TestMonitorReopenAfterFailedOpen.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 2 failed.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
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
     execute --select-class org.apache.lucene.monitor.TestMonitorReopenAfterFailedOpen
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
