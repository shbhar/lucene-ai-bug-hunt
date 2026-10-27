# TaxonomyFacetFloatAssociations with a DoubleValuesSource reports a too-high LabelAndValue.count for every label that has matching documents below it in a hierarchical dimension, because FloatTaxonomyFacets rolls the counts up again at every ancestor level (values are right; every release since 9.11.0)

Bug LFV-539 · Area: facet / taxonomy association facets (TaxonomyFacetFloatAssociations value-source path, FloatTaxonomyFacets#updateValueFromRollup count roll-up) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-539.html](../../bugs/LFV-539.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index documents with a FacetField in a dimension declared hierarchical (FacetsConfig.setHierarchical("d", true)), for example d/a/x with weight 7 and d/a/y with weight 2, and sum a per-document DoubleValuesSource per label with TaxonomyFacetFloatAssociations; you expect label a under d to report value 9 and count 2, the 'Number of occurrences for this label' that FastTaxonomyFacetCounts reports for the same hits. Instead getTopChildren and getAllChildren report a (9.0) with count 4, because FloatTaxonomyFacets#updateValueFromRollup first calls super.updateValueFromRollup, which rolls up the counts of the whole sub-tree, and then its own rollup(childOrdinal), which calls updateValueFromRollup again for every descendant, so the sub-tree counts are added once more at each ancestor level. Values and leaf counts are right; only LabelAndValue.count of labels with descendants is inflated, and the deeper the tree, the larger the overcount.

## Expected vs actual

- Expected: `a (9.0)` with count 2 from `getTopChildren` and `getAllChildren`, as `FastTaxonomyFacetCounts` reports; counts 1, 1, 1 along `d/a/b/x`.
- Actual: `a (9.0)` with count 4 from both methods (`[4, 4]`), and counts `[3, 3, 1]` along `d/a/b/x`; every value is right.

## Files

- [src/org/apache/lucene/facet/taxonomy/TestFloatValueSourceRollupCounts.java](src/org/apache/lucene/facet/taxonomy/TestFloatValueSourceRollupCounts.java) (JUnit test)

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
     execute --select-class org.apache.lucene.facet.taxonomy.TestFloatValueSourceRollupCounts
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestFloatAssociationRollupCounts.java](TestFloatAssociationRollupCounts.java)
- [TestFloatValueSourceRollupCounts.java](TestFloatValueSourceRollupCounts.java)
- [TestValueSourceAssociationCounts.java](TestValueSourceAssociationCounts.java)
- [TestRollupCountSemantics.java](TestRollupCountSemantics.java)
- [TestSumValueSourceRollupCounts9x.java](TestSumValueSourceRollupCounts9x.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
