# Taxonomy Facets#getAllDims sorts float-valued dims with a NaN-unsafe &lt; / &gt; comparator, so one dim whose value is NaN puts the other dims out of value order, breaks the documented rule that getTopDims equals the first entries of getAllDims, and with 32 or more dims can make getAllDims throw 'Comparison method violates its general contract!' (released since 4.7.0)

Bug LFV-820 · Area: facet / taxonomy facets (TaxonomyFacets#getAllDims ordering of float-valued dims: float association and value-source facets) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You aggregate float values per taxonomy dim (FloatAssociationFacetField weights or a DoubleValuesSource) and call getAllDims, documented to sort dims by value, and getTopDims, documented to return the same as the first entries of getAllDims. If one dim's value is NaN (dims a=1, b=NaN, c=2), getAllDims can return [a=1.0, b=NaN, c=2.0], with a ahead of c, while getTopDims(1, 10) returns [b=NaN]; with 32 or more dims, getAllDims can instead throw IllegalArgumentException 'Comparison method violates its general contract!' from List.sort. The getAllDims comparator compares doubleValue with &gt; in both directions and falls back to the dim name, so NaN ties with every value and the order is not transitive, while getTopDims has used the NaN-safe Float.compare (valueComparator) since #12966 (9.11.0).

The test shows that taxonomy `Facets#getAllDims` (float associations, SUM) misorders finite dims when another dim's value is NaN, because its &lt; / &gt; comparator treats NaN as equal to every value (dims a=1, b=NaN, c=2).

## Expected vs actual

Expected c=2.0 ranked before a=1.0 (getAllDims sorts by value); actual: `[a=1.0, b=NaN, c=2.0]`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/facet/taxonomy/TestGetAllDimsNaN.java](src/org/apache/lucene/facet/taxonomy/TestGetAllDimsNaN.java) (JUnit test)

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
     execute --select-class org.apache.lucene.facet.taxonomy.TestGetAllDimsNaN
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/facet-top-ordinal-queues-nan-and-special-value-order/](../../models/facet-top-ordinal-queues-nan-and-special-value-order/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
