# Monitor.purgeCache after a register whose commit or post-commit refresh failed with an I/O error rebuilds the query cache from the stale searcher and drops that query, although IndexWriter still publishes it at the next commit: getQueryIds()/getQuery() then list the query but match() never reports it until the Monitor is reopened (since 8.2.0)

Finding LFV-1477 · Area: monitor / WritableQueryIndex query cache purge vs. commit and searcher refresh (failure atomicity) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1477.html](../../bugs/LFV-1477.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You register q1 = f:apple on a Monitor with a persistent query index and the index's commit fails once with an IOException (here a single Directory.sync failure), so register() throws; then purgeCache() runs (explicitly, or as the default background purge) and a later register(q2) succeeds. You expect the Monitor to agree with itself: IndexWriter kept q1's document and publishes it with q2's commit, so a query that getQueryIds() and getQuery("q1") report as stored should match a document containing 'apple'. Instead match() never reports q1, because purgeCache rebuilt the query cache by scanning the searcher that the failed commit left stale, dropped q1's cache entry and never adds it back; only closing and reopening the Monitor, or registering q1 again, makes it match.

## Expected vs actual

- Expected: q1, which `getQuery("q1")` reports as stored, matches the document `apple`.
- Actual: `getQuery("q1")` returns `apple` but `match()` reports no query: `q1 stored=true but matched=false`.

## Files

- [src/org/apache/lucene/monitor/TestMonitorPurgeAfterFailedCommit.java](src/org/apache/lucene/monitor/TestMonitorPurgeAfterFailedCommit.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:$LUCENE/lucene/monitor/build/libs/lucene-monitor-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.monitor.TestMonitorPurgeAfterFailedCommit
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestMonitorPurgeAfterFailedCommit.java](TestMonitorPurgeAfterFailedCommit.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
