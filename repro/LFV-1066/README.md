# SearcherLifetimeManager.prune orders searchers by the time each was first recorded instead of newest to oldest, so when an older searcher is first recorded after the current one, PruneByAge drops the current searcher (even right after record() returned a token for it) and keeps the older one

Finding LFV-1066 · Area: core / search: SearcherLifetimeManager (keeping old searchers for follow-up requests such as paging) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1066.html](../../bugs/LFV-1066.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You use SearcherManager with SearcherLifetimeManager as the javadoc shows: request A acquires searcher S1, the manager refreshes to S2, request B acquires and records S2, and then A records S1. You expect prune to pass the searchers newest to oldest, as its javadoc says, so PruneByAge never drops S2, the current searcher. Instead prune sorts them by the time each was first recorded, so S1 goes first with age 0 and S2 is aged from S1's late record: 250 ms later, request C records S2 and gets a token, prune(new PruneByAge(0.1)) drops S2 anyway, acquire(token) returns null, and the older S1 is kept.

## Expected vs actual

- Expected: `acquire(token)` returns the current searcher: it is the newest, so `PruneByAge(0)` keeps it.
- Actual: `acquire(token)` returns null (`AssertionError` at line 42): prune dropped the current searcher and kept the older one.

## Files

- [src/org/apache/lucene/search/TestSearcherLifetimeManagerPruneOrder.java](src/org/apache/lucene/search/TestSearcherLifetimeManagerPruneOrder.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
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
     execute --select-class org.apache.lucene.search.TestSearcherLifetimeManagerPruneOrder
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestSearcherLifetimeManagerPruneOrder.java](TestSearcherLifetimeManagerPruneOrder.java)

Formal models and scripts this finding's part of the hunt used: [../../models/searcherlifetime-prune-order/](../../models/searcherlifetime-prune-order/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
