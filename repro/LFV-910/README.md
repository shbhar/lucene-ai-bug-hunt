# GroupingSearch.setCaching / setCachingInMB limit is applied per slice since #16247 (10.6.0 release-candidate regression): with an IndexSearcher that has an executor, a query matching more documents than the limit is replayed from per-slice caches holding up to slices x limit instead of being purged and re-run (results unchanged)

Bug LFV-910 · Area: grouping / GroupingSearch two-pass caching x search / CachingCollectorManager (new in 10.6.0) under a concurrent IndexSearcher · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You enable caching for a grouped search with GroupingSearch.setCaching(600, true) (or setCachingInMB) and search through an IndexSearcher that has an executor, and expect what the unchanged javadoc says and what 10.5.2 and a sequential searcher do: the cache holds at most the limit, and when the query matches more it is purged and the second pass re-runs the query. Instead, since #16247 GroupingSearch hands the limit to the new CachingCollectorManager, which applies it to each slice separately, so with 1000 matching documents over 2 slices each slice cache stays under 600, the caches hold all 1000 documents, and the second pass is replayed (the query runs once instead of twice). Results are the same; what breaks is the documented memory bound and the replay decision, and the cache can reach (number of slices) x limit.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: Since #16247 GroupingSearch hands the limit to the new CachingCollectorManager, which applies it to each slice separately, so with 1000 matching documents over 2 slices each slice cache stays under 600, the caches hold all 1000 documents, and the second pass is replayed (the query runs once instead of twice).

## Files

- [src/org/apache/lucene/search/grouping/TestGroupingSearchCacheLimitPerSlice.java](src/org/apache/lucene/search/grouping/TestGroupingSearchCacheLimitPerSlice.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/grouping/build/libs/lucene-grouping-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.grouping.TestGroupingSearchCacheLimitPerSlice
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/groupingsearch-cache-limit-applied-per-slice-10-6/](../../models/groupingsearch-cache-limit-applied-per-slice-10-6/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
