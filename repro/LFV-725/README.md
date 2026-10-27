# DrillSideways built with both a TaxonomyReader and a SortedSetDocValuesReaderState (the constructors documented for indexes that mix FacetField and SortedSetDocValuesFacetField dimensions) never counts the SortedSetDocValues dimensions, because the default buildFacetsResult ignores the state whenever taxoReader != null (every release since 4.7.0)

Bug LFV-725 · Area: facet / DrillSideways (default buildFacetsResult for the mixed TaxonomyReader + SortedSetDocValuesReaderState constructors) · Priority: medium · Impact: wrong error · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a taxonomy dimension color with FacetField and a SortedSetDocValues dimension size with SortedSetDocValuesFacetField in its own index field (indexing rejects both kinds in one field), create new DrillSideways(searcher, config, taxoReader, state), whose javadoc says it is for indexes 'where some dimensions were indexed with SortedSetDocValuesFacetField and others were indexed with FacetField', and drill down on color/red; you expect result.facets.getTopChildren(10, "size") to return the size counts of the matching documents (large 3, small 1), as SortedSetDocValuesFacetCounts over the result's own drillDownFacetsCollector does. Instead it throws IllegalArgumentException 'dimension "size" cannot be found in field "$facets", since it was configured to be indexed into field "ssdv_f"', because the default buildFacetsResult builds FastTaxonomyFacetCounts on the default field for the drill-down and for every sideways dimension whenever taxoReader != null and never reads the state. In the other layout (size on $facets, color in its own field) size silently returns null and is missing from getAllDims.

## Expected vs actual

- Expected: You expect result.facets.getTopChildren(10, "size") to return the size counts of the matching documents (large 3, small 1), as SortedSetDocValuesFacetCounts over the result's own drillDownFacetsCollector does.
- Actual: It throws IllegalArgumentException 'dimension "size" cannot be found in field "$facets", since it was configured to be indexed into field "ssdv_f"', because the default buildFacetsResult builds FastTaxonomyFacetCounts on the default field for the drill-down and for every sideways dimension whenever taxoReader != null and never reads the state.

## Files

- [src/org/apache/lucene/facet/TestDrillSidewaysMixedFacets.java](src/org/apache/lucene/facet/TestDrillSidewaysMixedFacets.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.facet.TestDrillSidewaysMixedFacets
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
