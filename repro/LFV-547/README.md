# Association facets with AssociationAggregationFunction.MAX report 0 instead of the maximum when every value of a label, of a single-valued dimension or of a rolled-up hierarchy node is negative, because the maximum starts from 0 (int, float and DoubleValuesSource paths; every release since 9.2.0)

Bug LFV-547 · Area: facet / taxonomy association facets (TaxonomyFacetIntAssociations, TaxonomyFacetFloatAssociations incl. the DoubleValuesSource path; MAX start value in the per-ordinal, roll-up and dimension aggregates) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index two documents whose IntAssociationFacetField values for the label int/x are -3 and -5 (or float associations -1.5 and -2.5, or a DoubleValuesSource over a field holding -4 and -7) and facet them with AssociationAggregationFunction.MAX, the 'Aggregation that computes the maximum value'. You expect -3 (-1.5, -4.0), since the 9.11.0 CHANGES say aggregation facets no longer assume that values are positive. Instead getTopChildren, getAllChildren, getSpecificValue and the dimension value all report 0 (0.0), because the first value of every ordinal is combined with the 0 of an unseen ordinal, Math.max(0, v), and the roll-up and dimension aggregates also start at 0.

## Expected vs actual

- Expected: You expect -3 (-1.5, -4.0), since the 9.11.0 CHANGES say aggregation facets no longer assume that values are positive.
- Actual: getTopChildren, getAllChildren, getSpecificValue and the dimension value all report 0 (0.0), because the first value of every ordinal is combined with the 0 of an unseen ordinal, Math.max(0, v), and the roll-up and dimension aggregates also start at 0.

## Files

- [src/org/apache/lucene/facet/taxonomy/TestAssociationMaxOfNegativeValues.java](src/org/apache/lucene/facet/taxonomy/TestAssociationMaxOfNegativeValues.java) (JUnit test)

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
     execute --select-class org.apache.lucene.facet.taxonomy.TestAssociationMaxOfNegativeValues
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
