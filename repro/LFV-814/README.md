# FacetsCollectorManager#search/searchAfter with a Sort and DrillSideways#search(..., Sort,...) never call Sort#rewrite(searcher), so a query-dependent sort returns a different hit order than IndexSearcher#search(query, n, sort) or throws

Bug LFV-814 · Area: facet / sorted-search helpers (FacetsCollectorManager static search/searchAfter, DrillSideways sorted search) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run a faceted search with FacetsCollectorManager.search(searcher, query, n, sort, fcm) or DrillSideways.search(query, filter, after, n, sort, doDocScores), sorted by a function of a query's score, and expect the same hits, order and sort values as IndexSearcher#search(query, n, sort), which rewrites the Sort against the searcher first. Instead both helpers hand the caller's Sort to TopFieldCollectorManager as given: on a ClassicSimilarity searcher a QueryValueSource(body:a) sort returns [doc0=0.147, doc1=0.125] (scores from a fresh default-BM25 searcher) where IndexSearcher#search returns [doc1=1.0, doc0=0.894]. A DoubleValuesSource.fromQuery sort throws 'This DoubleValuesSource must be rewritten' from both helpers, and a ValueSource#getSortField sort throws 'SortField needs to be rewritten'.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: Both helpers hand the caller's Sort to TopFieldCollectorManager as given: on a ClassicSimilarity searcher a QueryValueSource(body:a) sort returns [doc0=0.147, doc1=0.125] (scores from a fresh default-BM25 searcher) where IndexSearcher#search returns [doc1=1.0, doc0=0.894].

## Files

- [src/org/apache/lucene/facet/TestFacetSortedSearchRewritesSort.java](src/org/apache/lucene/facet/TestFacetSortedSearchRewritesSort.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.facet.TestFacetSortedSearchRewritesSort
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
