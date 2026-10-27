# A read-only Monitor's purgeCache returns without refreshing when another refresh is running, so a query committed before the call can stay invisible after it returns

Bug LFV-773 · Area: monitor / read-only query index (ReadonlyQueryIndex.purgeCache refresh) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You open a read-only Monitor over a query index that a writable Monitor updates, register and commit a new query with the writer, and call purgeCache on the read-only Monitor, whose javadoc says read-only users 'MUST call it when you want new changes'. You expect the new query to match once the call returns. Instead, if another refresh of the same Monitor (its own background refresh, or another thread's purgeCache) is already running and has read the older commit, purgeCache calls the non-blocking SearcherManager.maybeRefresh, which returns false at once, drops that result and still notifies the onPurge listeners; the running refresh then installs the older commit, so the new query is still unmatched after both have returned, until some later refresh.

## Expected vs actual

- Expected: You expect the new query to match once the call returns.
- Actual: If another refresh of the same Monitor (its own background refresh, or another thread's purgeCache) is already running and has read the older commit, purgeCache calls the non-blocking SearcherManager.maybeRefresh, which returns false at once, drops that result and still notifies the onPurge listeners; the running refresh then installs the older commit, so the new query is still unmatched after both have returned, until some later refresh.

## Files

- [src/org/apache/lucene/monitor/TestReadonlyMonitorExplicitRefresh.java](src/org/apache/lucene/monitor/TestReadonlyMonitorExplicitRefresh.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:$LUCENE/lucene/monitor/build/libs/lucene-monitor-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.monitor.TestReadonlyMonitorExplicitRefresh
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
