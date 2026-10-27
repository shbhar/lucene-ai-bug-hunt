# Taxonomy facets never roll up a single-valued hierarchical dimension whose config comes from an overridden FacetsConfig#getDefaultDimConfig, so its dimension and non-leaf counts are 0 and getTopChildren returns null or partial results

Bug LFV-723 · Area: facet / taxonomy facets (TaxonomyFacets#rollup dimension enumeration vs the overridable FacetsConfig default dimension config; FastTaxonomyFacetCounts, TaxonomyFacetFloatAssociations) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You subclass FacetsConfig so that every dimension without an explicit entry is hierarchical (override getDefaultDimConfig to return a DimConfig with hierarchical = true, as TestFacetsConfig#testCustomDefault does), index three documents Publish Date/2010/10/15, Publish Date/2010/10/20 and Publish Date/2012/1/1, count them with FastTaxonomyFacetCounts, and expect what an explicit setHierarchical("Publish Date", true) gives: value=3, 2010 (2), 2012 (1). FacetsConfig.build follows the default and indexes only the leaf ordinals, relying on the search-time roll-up, but TaxonomyFacets.rollup loops over config.getDimConfigs, which lists only explicitly configured dimensions, so the roll-up never runs. getTopChildren(10, "Publish Date") returns null and getSpecificValue("Publish Date") and getSpecificValue("Publish Date", "2010") return 0, while a DrillDownQuery on Publish Date/2010 still matches 2 documents.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/facet/taxonomy/TestDefaultDimConfigRollup.java](src/org/apache/lucene/facet/taxonomy/TestDefaultDimConfigRollup.java) (JUnit test)

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
     execute --select-class org.apache.lucene.facet.taxonomy.TestDefaultDimConfigRollup
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
