# SortedSetDocValues and taxonomy Facets#getTopDims size their dimension priority queue by the caller's topNDims without a cap, so getTopDims(Integer.MAX_VALUE, n) throws IllegalArgumentException where getAllDims(n) and the default Facets#getTopDims return every dim, and a large topNDims allocates topNDims + 1 queue slots even for 2 dims

Bug LFV-983 · Area: facet / getTopDims dimension queue (AbstractSortedSetDocValueFacetCounts and TaxonomyFacets overrides, inherited by SortedSetDocValuesFacetCounts, ConcurrentSortedSetDocValuesFacetCounts, FastTaxonomyFacetCounts, TaxonomyFacetIntAssociations and TaxonomyFacetFloatAssociations) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index two flat facet dims, call getAllDims(10) on SortedSetDocValuesFacetCounts or FastTaxonomyFacetCounts and get both dims, then call getTopDims(Integer.MAX_VALUE, 10) to ask for 'all dims' and expect the same list, because the getTopDims javadoc says results should be the same as calling getAllDims and then only using the first topNDims, and the default Facets#getTopDims caps with Math.min(topNDims, allResults.size). Instead the optimized overrides in AbstractSortedSetDocValueFacetCounts and TaxonomyFacets build PriorityQueue.usingComparator(topNDims,...) before they look at any dim, so the call throws IllegalArgumentException 'maxSize must be &gt;= 0 and &lt; 2147483631; got: 2147483647' in all five SortedSetDocValues and taxonomy implementations. Below that limit the same line pre-allocates topNDims + 1 slots: getTopDims(2^24, 10) over 2 dims allocates about 64 MiB to return 2 results.

The test shows that `SortedSetDocValuesFacetCounts.getTopDims(Integer.MAX_VALUE, 10)` sizes its dim queue by `topNDims` without a cap and throws, where `getAllDims(10)` returns both dims.

## Expected vs actual

Expected: the same 2 dims as `getAllDims(10)` (the `Facets#getTopDims` contract); actual: `IllegalArgumentException: maxSize must be >= 0 and < 2147483631; got: 2147483647`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/facet/sortedset/TestGetTopDimsAllDims.java](src/org/apache/lucene/facet/sortedset/TestGetTopDimsAllDims.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.facet.sortedset.TestGetTopDimsAllDims
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/facets-topn-and-topndims-queues-presized-uncapped/](../../models/facets-topn-and-topndims-queues-presized-uncapped/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
