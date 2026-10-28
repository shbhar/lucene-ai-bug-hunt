# A negative cache limit (GroupingSearch.setCaching(-1,..) / setCachingInMB(-1.0,..), CachingCollector.create(.., -1), CachingCollectorManager) is accepted and caches nothing, yet isCached stays true, so replay delivers no documents and grouped search silently returns groups with no documents and totalHitCount 0 (since 9.8.0; from 5.0.0 to 9.7.0 replay threw instead)

Bug LFV-908 · Area: search / CachingCollector first-pass caching (CachingCollector, CachingCollectorManager) x grouping GroupingSearch two-pass caching · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-908.html](../../bugs/LFV-908.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You enable caching for a grouped search (or wrap a first-pass collector in CachingCollector or CachingCollectorManager) with a negative limit, for example setCaching(-1, true) meant as 'no limit', and expect either an IllegalArgumentException or the documented fallback for a cache over its limit: isCached false and the query re-run. Instead the limit is accepted and nothing is cached (a negative count is the collector's own internal 'purged' marker), but isCached stays true, so replay delivers none of the collected documents and GroupingSearch returns its top groups with no documents and totalHitCount 0 instead of 20 in the repro, with no error. From 5.0.0 to 9.7.0 the same input failed at replay with an AssertionError (-ea) or IndexOutOfBoundsException (-da).

## Expected vs actual

- Expected: `totalHitCount=20 [author0=7, author1=7, author2=6]`, as without caching (or an `IllegalArgumentException`), and `replay` delivers all 20 documents.
- Actual: `totalHitCount=0 [author0=0, author1=0, author2=0]` for both setters, and `replay` delivers 0 of 20 with `isCached` true; no error, assertions on or off.

## Files

- [src/org/apache/lucene/search/grouping/TestCachingNegativeLimit.java](src/org/apache/lucene/search/grouping/TestCachingNegativeLimit.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.grouping.TestCachingNegativeLimit
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestGroupingSearchNegativeCacheLimit.java](TestGroupingSearchNegativeCacheLimit.java)
- [TestCachingNegativeLimit.java](TestCachingNegativeLimit.java)
- [NegativeCacheLimitAcrossReleases.java](NegativeCacheLimitAcrossReleases.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
