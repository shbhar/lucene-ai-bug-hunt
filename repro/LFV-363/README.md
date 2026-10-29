# Taxonomy Facets#getTopDims returns another dimension's children, childCount and value under a multi-valued dimension's name, because replacing the bottom of the top-dims queue updates dim and value but not dimOrd (released since 9.2.0)

Bug LFV-363 · Area: facet / taxonomy facets (TaxonomyFacets#getTopDims, shared by FastTaxonomyFacetCounts and the int and float association facets) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-363.html](../../bugs/LFV-363.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You mark taxonomy facet dims as multi-valued, run a query and call getTopDims(1, 10), and expect the same as getAllDims(10).subList(0, 1), as the Facets#getTopDims javadoc says. When the winning dim is visited after the dim it pushes out of the internal top-dims queue (dim x with 2 hits, created first, and dim y with 1 hit, both with requireDimCount), Lucene returns 'dim=x value=1 childCount=1 y1 (1)' instead of 'dim=x value=2 childCount=2 x1 (1) x2 (1)', that is y's children and count under x's name. The queue-replacement branch sets bottomDim.dim and bottomDim.value but never bottomDim.dimOrd, and the children of multi-valued dims are then computed from that stale ordinal.

The test shows that taxonomy `getTopDims(1, 10)` returns dim y's value, childCount and children under dim x's name when x (multi-valued, requireDimCount, created first, 2 hits) replaces y (1 hit) in the top-dims queue, because the replacement keeps y's `dimOrd`.

## Expected vs actual

Expected (Facets#getTopDims javadoc: same as getAllDims, first topNDims): `dim=x value=2 childCount=2 x1 (1) x2 (1)`; actual (-ea and -da): `dim=x value=1 childCount=1 y1 (1)`.

## Files

- [src/org/apache/lucene/facet/taxonomy/TestTaxonomyGetTopDimsMultiValued.java](src/org/apache/lucene/facet/taxonomy/TestTaxonomyGetTopDimsMultiValued.java) (JUnit test)

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
     execute --select-class org.apache.lucene.facet.taxonomy.TestTaxonomyGetTopDimsMultiValued
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
