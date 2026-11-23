# LRUQueryCache on main and branch_11x picks an entry's partition from the unmixed low bits of Query.hashCode(), so all exact numeric filters of one kind on a field share one of the 16 partitions and evict each other: 100 exact IntPoint filters on one segment get 0 cache hits where 10.6.0 serves all 100 (performance only, unreleased; 11.0.0 would ship it)

Finding LFV-1490 · Area: search / query cache (LRUQueryCache partition routing: the partitioned cache of #15558 as changed by #16657; main and branch_11x only) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1490.html](../../bugs/LFV-1490.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

On main or branch_11x you turn the query cache on the way MIGRATE.md shows (new LRUQueryCache(1_000, 50 MB), the default caching policy) and run 100 different exact filters on one int field (IntPoint.newExactQuery) three times each over a one-segment index. You expect the third use of each filter to be served from the cache, as on 10.6.0: the filters need 100 of the cache's 1,000 entries. Instead not one lookup is a cache hit (0 of 100; 10.6.0: 100 of 100), because the cache picks one of its 16 partitions from the lowest four bits of the query's hash code, those bits are the same for every exact numeric filter of one kind on a field, and that one partition holds only 62 entries, so the filters keep evicting each other. No search result is wrong: only the benefit of the cache is lost. No release has this code.

## Expected vs actual

- Expected: `cache.getHitCount()` is 100: the third use of each of the 100 filters is served from the cache, as on 10.6.0.
- Actual: The hit count is 0 (`expected:<100> but was:<0>` at line 54) on main and on the 11.0 branch.

## Files

- [src/org/apache/lucene/search/TestLRUQueryCacheExactPointFilters.java](src/org/apache/lucene/search/TestLRUQueryCacheExactPointFilters.java) (JUnit test)

## Run it

- Tested on Lucene main 11c8b6ea99ae (a commit of 2026-11-23): still reproduces.
- Tested on Lucene main 781025d6fe32 (a commit of 2026-11-13): reproduced.
- What the test does: it writes 10,000 documents into one segment, installs new LRUQueryCache(1_000, 50 MB) with UsageTrackingQueryCachingPolicy and searches 100 different IntPoint.newExactQuery filters, each wrapped in ConstantScoreQuery, three times each. It asserts that LRUQueryCache.getHitCount() is 100 and gets 0: the default policy caches a point query on its second use, so only the third use of each filter can be a hit. The expected 100 is what the same test counts on the 10.6.0 release, not a documented number. The test checks cache hits only; it compares no search result, and none is wrong. Ranges of one width, which the finding also covers, are not part of this test, nor is the earlier form with 20 filters over 10 segments that the table's record reports.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual, or names the exception that was thrown).
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

# run 1: assertions on, seed 5EED7611
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=4 -Dtests.seed=5EED7611 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestLRUQueryCacheExactPointFilters
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestLRUQueryCacheExactPointFilters.java](TestLRUQueryCacheExactPointFilters.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
