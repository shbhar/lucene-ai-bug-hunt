# Facets#getTopDims on taxonomy and SortedSetDocValues facets admits a multi-valued dimension without requireDimCount even when the query has no hits in it, so the result list contains null (AssertionError with -ea) and a dimension that has hits can be pushed out (released since 9.2.0)

Bug LFV-368 · Area: facet / getTopDims of taxonomy and SortedSetDocValues facets (TaxonomyFacets#getTopDims and AbstractSortedSetDocValueFacetCounts#getTopDims, shared by FastTaxonomyFacetCounts, the taxonomy int and float association facets, SortedSetDocValuesFacetCounts and ConcurrentSortedSetDocValuesFacetCounts) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-368.html](../../bugs/LFV-368.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You mark facet dimensions a and b as multi-valued (FacetsConfig#setMultiValued, requireDimCount left at its default false), index one document with a=a1 and one with b=b1, search for the second document only and call getTopDims(10, 10), expecting [b], the same as getAllDims(10), as the Facets#getTopDims javadoc says. Taxonomy and SortedSetDocValues facets instead return [null, b] (with -ea, getTopDims throws AssertionError at Lucene's own 'assert facetResult != null'), and SSDV getTopDims(1, 10) returns [null], dropping b, the only dimension with a hit. For such dimensions getTopDims uses the placeholder value -1 without checking for hits, the admission test 'value != 0' lets -1 through, and the per-dimension result of a dimension without hits is null.

## Expected vs actual

- Expected: `getTopDims(10, 10)` and `getTopDims(1, 10)` both return `[b]`, the first entries of `getAllDims(10)`.
- Actual: `[null, b]` and `[null]` with assertions off; with assertions on, an `AssertionError` inside `getTopDims`.

## Files

- [src/org/apache/lucene/facet/TestGetTopDimsDimWithoutHits.java](src/org/apache/lucene/facet/TestGetTopDimsDimWithoutHits.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.facet.TestGetTopDimsDimWithoutHits
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestGetTopDimsMultiValuedDimWithoutHits.java](TestGetTopDimsMultiValuedDimWithoutHits.java)
- [TestGetTopDimsDimWithoutHits.java](TestGetTopDimsDimWithoutHits.java)
- [TestTopDimsMultiValuedDimNoHits.java](TestTopDimsMultiValuedDimNoHits.java)
- [TestGetTopDimsAssocDimWithoutHits.java](TestGetTopDimsAssocDimWithoutHits.java)
- [TestGetTopDimsNoHitDimHistory.java](TestGetTopDimsNoHitDimHistory.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
