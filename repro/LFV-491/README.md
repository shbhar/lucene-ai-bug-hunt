# TaxonomyMergeUtils.merge / OrdinalMappingLeafReader do not re-map the taxonomy ordinals stored in association facet fields, so merged IntAssociationFacetField / FloatAssociationFacetField values are credited to unrelated categories (10.x, main) or lost (9.x)

Bug LFV-491 · Area: facet / taxonomy (TaxonomyMergeUtils.merge, OrdinalMappingLeafReader) · Priority: medium · Impact: data corruption · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You merge an index and its taxonomy into another index/taxonomy pair with TaxonomyMergeUtils.merge ('Merges the given taxonomy and index directories'); the source documents carry IntAssociationFacetField values a/x=5 and a/y=7 and the destination already holds b/1=100 and b/2=200 in the same association field. You expect TaxonomyFacetIntAssociations on the merged index to report a: y=7 x=5 and b: 2=200 1=100. Instead, on main and 10.x it reports nothing for a and b: 2=207 1=105, with no error, because association ordinals live in binary doc values that OrdinalMappingLeafReader copies unchanged (it re-maps only sorted-numeric doc values); on 9.x the merged values are dropped (a: nothing, b: 2=200 1=100).

## Expected vs actual

- Expected: You expect TaxonomyFacetIntAssociations on the merged index to report a: y=7 x=5 and b: 2=200 1=100.
- Actual: On main and 10.x it reports nothing for a and b: 2=207 1=105, with no error, because association ordinals live in binary doc values that OrdinalMappingLeafReader copies unchanged (it re-maps only sorted-numeric doc values); on 9.x the merged values are dropped (a: nothing, b: 2=200 1=100).

## Files

- [src/org/apache/lucene/facet/taxonomy/TestTaxonomyMergeAssociationOrdinals.java](src/org/apache/lucene/facet/taxonomy/TestTaxonomyMergeAssociationOrdinals.java) (JUnit test)

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
     execute --select-class org.apache.lucene.facet.taxonomy.TestTaxonomyMergeAssociationOrdinals
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
