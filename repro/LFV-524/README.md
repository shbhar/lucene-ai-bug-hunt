# MultiFacets.getAllDims, the Facets of every DrillSideways result with a drill-down dim, lists the mapped (sideways) dims first in HashMap order instead of sorted by value and returns null for a mapped dim without hits, so the inherited getTopDims returns the wrong dims

Bug LFV-524 · Area: facet / MultiFacets and DrillSideways results (MultiFacets#getAllDims, the default DrillSideways#buildFacetsResult, the inherited Facets#getTopDims) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run a DrillSideways search that drills down on Author=Lisa and Publish Date=2010, where the sideways counts are Author 3 and Publish Date 1, and call getAllDims(10) on the result's facets, expecting what Facets#getAllDims documents, the dims that had hits sorted by value ([Author (3), Publish Date (1)]), with getTopDims(1, 10) returning its first entry. Instead the MultiFacets that DrillSideways returns lists the sideways dims in HashMap order without sorting ([Publish Date (1), Author (3)]), so getTopDims(1, 10) returns Publish Date, and when the base query has no hits getAllDims returns [null] where FastTaxonomyFacetCounts returns . The cause is MultiFacets#getAllDims, which appends getTopChildren of every mapped dim without a null check, then the default Facets' dims, and never sorts.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The MultiFacets that DrillSideways returns lists the sideways dims in HashMap order without sorting ([Publish Date (1), Author (3)]), so getTopDims(1, 10) returns Publish Date, and when the base query has no hits getAllDims returns [null] where FastTaxonomyFacetCounts returns .

## Files

- [src/org/apache/lucene/facet/TestMultiFacetsAllDimsContract.java](src/org/apache/lucene/facet/TestMultiFacetsAllDimsContract.java) (JUnit test)

## Run it

- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
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
     execute --select-class org.apache.lucene.facet.TestMultiFacetsAllDimsContract
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
