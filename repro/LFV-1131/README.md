# Facet-set DimRange.fromDoubles/fromFloats (and range-on-range DoubleRange) make an exclusive bound inclusive with Math.nextUp/Math.nextDown, which skip the other signed zero, so a facet set holding -0.0 is counted by neither of the adjacent ranges [-1, 0) and [0, 1]

Finding LFV-1131 · Area: facet / facetset and rangeonrange (exclusive-bound stepping of double and float ranges: facetset.DimRange.fromDoubles/fromFloats with RangeFacetSetMatcher, rangeonrange.DoubleRange with DoubleRangeOnRangeFacetCounts) · Priority: low · Impact: silent wrong results · Novelty: new

Finding page: [../../bugs/LFV-1131.html](../../bugs/LFV-1131.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index one document with a DoubleFacetSet holding -0.0 and count it with two RangeFacetSetMatchers, DimRange.fromDoubles(-1, true, 0, false) and DimRange.fromDoubles(0, true, 1, true), which split [-1, 1] at zero; you expect the two counts to add up to 1, because -0.0 belongs to [-1, 0) if the zeros are ordered as Double.compare orders them (the sortable order the matcher compares in) and to [0, 1] if they compare equal. Instead both counts are 0, with no error, while a covering [-1, 1] range counts the document: fromDoubles turns the exclusive max 0 into Math.nextDown(+0.0) = -Double.MIN_VALUE, which in sortable order lies below -0.0, so -0.0 falls between the two ranges. fromFloats, the mirror case (+0.0 against [-1, -0.0] and (-0.0, 1], where Math.nextUp(-0.0) skips +0.0) and range-on-range DoubleRange behave the same way.

## Expected vs actual

- Expected: The two counts add up to 1: -0.0 is counted by exactly one of `[-1, 0)` and `[0, 1]`.
- Actual: Both counts are 0: `AssertionError: [-1, 0) + [0, 1] expected:<1> but was:<0>`.

## Files

- [src/org/apache/lucene/facet/facetset/TestDimRangeExclusiveZeroBound.java](src/org/apache/lucene/facet/facetset/TestDimRangeExclusiveZeroBound.java) (JUnit test)

## Run it

- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.facet.facetset.TestDimRangeExclusiveZeroBound
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestDimRangeExclusiveZeroBound.java](TestDimRangeExclusiveZeroBound.java)

Formal models and scripts this finding's part of the hunt used: [../../models/facetset-dimrange-and-rangeonrange-exclusive-bound-signed-zero-gap/](../../models/facetset-dimrange-and-rangeonrange-exclusive-bound-signed-zero-gap/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
