# Monitor.match running while Monitor.register re-registers the same query id can silently miss that query, because register puts the new version's cache entries into the live cache map before the new index documents are committed and the query index searcher is refreshed

Bug LFV-772 · Area: monitor / query index updates versus matching (WritableQueryIndex.commitWithoutNotify versus search, reached through Monitor.register and Monitor.match) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-772.html](../../bugs/LFV-772.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

Query q = apple OR banana is registered in a Monitor; one thread re-registers q as cherry OR apple while another thread matches a document that contains apple. You expect the match to report q, because both versions of q match the document and every serial order of the two calls reports it. Instead the match can return no queries: register has already replaced the cache entry q_0 with the new first clause (cherry) while the searcher still holds the old index documents, so the old document for apple resolves to the cherry clause, which does not match, and the new apple clause is not searchable yet; once register returns, the same match reports q again.

The test shows that a Monitor.match on another thread while Monitor.register re-registers q (apple OR banana -&gt; cherry OR apple) and is inside IndexWriter.commit() misses q (`TestMonitorMatchDuringReRegister#testMatchOnOtherThreadWhileReRegisterCommits`, one document).

## Expected vs actual

Expected: the match of 'apple' reports q, since both versions match it. Actual: no match (the cache already maps q_0 to cherry while the searcher still has the old apple document); fails with -ea and -da.

## Files

- [src/org/apache/lucene/monitor/TestMonitorMatchDuringReRegister.java](src/org/apache/lucene/monitor/TestMonitorMatchDuringReRegister.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
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
     execute --select-class org.apache.lucene.monitor.TestMonitorMatchDuringReRegister
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
