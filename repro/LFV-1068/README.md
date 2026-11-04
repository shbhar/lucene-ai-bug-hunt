# GroupingSearch.setCaching(n, false) and setCachingInMB(mb, false) throw NullPointerException with the default within-group sort or the default includeMaxScore, because the score-less cache is replayed into a second pass that needs scores

Finding LFV-1068 · Area: grouping / GroupingSearch second-pass cache (score-less CachingCollector / CachingCollectorManager replay into the TopGroups second pass) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1068.html](../../bugs/LFV-1068.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You turn on GroupingSearch's documented second-pass cache without cached scores, new GroupingSearch("g").setCaching(10, false), and run an ordinary grouped search over two documents in groups A and B; you expect the same groups as without the cache, because the javadoc presents the cache as an optimisation that the second pass replays. Instead search() throws NullPointerException ('Cannot invoke Scorable.score() because this.scorer is null'): GroupingSearch builds the cache with the caller's cacheScores flag but replays it into a second pass that it builds itself from its defaults (within-group sort Sort.RELEVANCE, includeMaxScore=true), and both need scores, while a score-less replay never sets a Scorer. Whether a search fails depends on the hit count: when the hits overflow the cache, the cache is dropped, the query is re-run and the search succeeds.

## Expected vs actual

- Expected: `search()` returns 1 group (`A`) with its 1 document, as without the cache.
- Actual: `NullPointerException: Cannot invoke "Scorable.score()" because "this.scorer" is null` in `TopScoreDocCollector$1.collect`, via `CachingCollectorManager.replay`.

## Files

- [src/org/apache/lucene/search/grouping/TestGroupingSearchCachingWithoutScores.java](src/org/apache/lucene/search/grouping/TestGroupingSearchCachingWithoutScores.java) (JUnit test)

## Run it

- Tested on Lucene main e1faeba08bde (2026-11-02): still reproduces.
- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
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
     execute --select-class org.apache.lucene.search.grouping.TestGroupingSearchCachingWithoutScores
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestGroupingSearchCachingWithoutScores.java](TestGroupingSearchCachingWithoutScores.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
