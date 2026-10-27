# CachingCollector and CachingCollectorManager report isCached true (no overflow) but hold only the hits their wrapped first-pass collector let through (TOP_SCORES pruning, early leaf termination, skipped leaves), so replay silently feeds the second pass fewer matches than re-running the query

Bug LFV-161 · Area: search / CachingCollector first-pass caching (CachingCollector, CachingCollectorManager) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run a query once through a CachingCollector or CachingCollectorManager that wraps a first-pass collector, see isCached return true (the cache did not overflow), and replay the cached hits into a second collector, expecting the same result as running that collector on the query, which is what the javadocs and the grouping docs offer ('without re-running the query', 'hold all hits'). Instead the cache holds only the hits the first-pass collector let through: TopScoreDocCollectorManager(10, 1000), the IndexSearcher#search(Query, int) default, prunes non-competitive hits (even when the score-caching variant tells the searcher it needs COMPLETE), an index-order TopFieldCollector stops each segment early, and TotalHitCountCollector skips whole segments it can count from index statistics. In the repro, replaying into a hit counter sees 1001, 2 and 0 of 5000 matching documents, with no error.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The cache holds only the hits the first-pass collector let through: TopScoreDocCollectorManager(10, 1000), the IndexSearcher#search(Query, int) default, prunes non-competitive hits (even when the score-caching variant tells the searcher it needs COMPLETE), an index-order TopFieldCollector stops each segment early, and TotalHitCountCollector skips whole segments it can count from index statistics.

## Files

- [src/org/apache/lucene/search/TestCachingCollectorManagerIncompleteCache.java](src/org/apache/lucene/search/TestCachingCollectorManagerIncompleteCache.java) (JUnit test)
- [src/org/apache/lucene/search/TestCachingCollectorIncompleteCacheLegacy.java](src/org/apache/lucene/search/TestCachingCollectorIncompleteCacheLegacy.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestCachingCollectorManagerIncompleteCache

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestCachingCollectorIncompleteCacheLegacy
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
