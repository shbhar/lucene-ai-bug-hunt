# Int association facets with AssociationAggregationFunction.SUM add values in int, so a label total above Integer.MAX_VALUE silently wraps to a wrong, often negative, value (known: #6596 / LUCENE-5533) (released since 4.x)

Finding LFV-1116 · Area: facet / taxonomy association facets (TaxonomyFacetIntAssociations, IntTaxonomyFacets, AssociationAggregationFunction.SUM) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index two documents whose IntAssociationFacetField values for the label dim/a are 1,500,000,000 and 1,000,000,000 and aggregate them with TaxonomyFacetIntAssociations and AssociationAggregationFunction.SUM, the 'Aggregation that computes the sum'. You expect 2,500,000,000, but getSpecificValue returns -1,794,967,296 with no error: SUM adds in int and the per-label values are kept in an int array or int map, so any label or dimension total above Integer.MAX_VALUE wraps (about 2.2 million matches of value 1,000 are enough).

## Expected vs actual

- Expected: You expect 2,500,000,000, but getSpecificValue returns -1,794,967,296 with no error: SUM adds in int and the per-label values are kept in an int array or int map, so any label or dimension total above Integer.MAX_VALUE wraps (about 2.2 million matches of value 1,000 are enough).
- Actual: While the reported behaviour is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/facet/taxonomy/TestIntAssociationSumOverflow.java](src/org/apache/lucene/facet/taxonomy/TestIntAssociationSumOverflow.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces (the repro adapted to an API change on this commit).
- Tested on Lucene main e1faeba08bde (2026-11-02): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=3 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.facet.taxonomy.TestIntAssociationSumOverflow
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
