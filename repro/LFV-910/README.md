# GroupingSearch.setCaching / setCachingInMB limit is applied per slice since #16247 (10.6.0 release-candidate regression): with an IndexSearcher that has an executor, a query matching more documents than the limit is replayed from per-slice caches holding up to slices x limit instead of being purged and re-run (results unchanged)

Bug LFV-910 · Area: grouping / GroupingSearch two-pass caching x search / CachingCollectorManager (new in 10.6.0) under a concurrent IndexSearcher · Priority: low · Impact: minor · Novelty: new

Bug page: [../../bugs/LFV-910.html](../../bugs/LFV-910.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You enable caching for a grouped search with GroupingSearch.setCaching(600, true) (or setCachingInMB) and search through an IndexSearcher that has an executor, and expect what the unchanged javadoc says and what 10.5.2 and a sequential searcher do: the cache holds at most the limit, and when the query matches more it is purged and the second pass re-runs the query. Instead, since #16247 GroupingSearch hands the limit to the new CachingCollectorManager, which applies it to each slice separately, so with 1000 matching documents over 2 slices each slice cache stays under 600, the caches hold all 1000 documents, and the second pass is replayed (the query runs once instead of twice). Results are the same; what breaks is the documented memory bound and the replay decision, and the cache can reach (number of slices) x limit.

The test shows that with an executor searcher (6 one-doc segments, default slicing = 2 slices of 5 + 1 docs), `GroupingSearch.setCaching(5, true)` replays the first-pass cache although 6 docs were cached, because the limit is applied per slice (#16247).

## Expected vs actual

Expected: the cache is purged and the query re-run (2 query runs, as the setCaching javadoc says and 10.5.2 does); actual: `expected:<2> but was:<1>` (-ea and -da).

## Files

- [src/org/apache/lucene/search/grouping/TestGroupingSearchCacheLimit.java](src/org/apache/lucene/search/grouping/TestGroupingSearchCacheLimit.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/grouping/build/libs/lucene-grouping-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.grouping.TestGroupingSearchCacheLimit
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/groupingsearch-cache-limit-applied-per-slice-10-6/](../../models/groupingsearch-cache-limit-applied-per-slice-10-6/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
