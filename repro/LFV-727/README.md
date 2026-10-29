# Taxonomy facets: for a sub-path of a single-valued hierarchical dimension, FacetResult.value from getTopChildren/getAllChildren is the sum of the children, so documents labeled at the sub-path itself are left out, while getSpecificValue, drill-down and SortedSetDocValues faceting count them

Bug LFV-727 · Area: facet / taxonomy facets (TaxonomyFacets#getTopChildrenForPath and #getAllChildren: FacetResult.value of a sub-path; FastTaxonomyFacetCounts, TaxonomyFacetFloatAssociations with a DoubleValuesSource) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-727.html](../../bugs/LFV-727.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index two documents with taxonomy facets in a dimension configured only with setHierarchical("Publish Date", true): one labeled Publish Date/2010 and one labeled Publish Date/2010/10. Both documents have a value for the path Publish Date/2010 (a DrillDownQuery on it matches 2 and getSpecificValue("Publish Date", "2010") returns 2), so FacetResult.value of getTopChildren(10, "Publish Date", "2010") and getAllChildren("Publish Date", "2010") should be 2, as its javadoc ('Total number of documents containing a value for this path') and SortedSetDocValues faceting with the same config say. FastTaxonomyFacetCounts returns 1 for both: for a single-valued dimension it adds up the path's immediate children and never uses the path's own rolled-up count, so the document labeled at 2010 itself is left out.

for a sub-path of a single-valued hierarchical taxonomy dimension, `FacetResult.value` leaves out the documents labeled at the path itself (docs at `Publish Date/2010` and `Publish Date/2010/10`).

## Expected vs actual

Expected `getTopChildren(10, "Publish Date", "2010").value` = 2 (both documents contain a value for the path); actual 1 (only the child `2010/10` is summed).

## Files

- [src/org/apache/lucene/facet/taxonomy/TestHierarchicalSubPathValue.java](src/org/apache/lucene/facet/taxonomy/TestHierarchicalSubPathValue.java) (JUnit test)

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
     execute --select-class org.apache.lucene.facet.taxonomy.TestHierarchicalSubPathValue
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
