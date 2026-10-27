# Facet DoubleRange, range-on-range DoubleRange and DimRange.fromDoubles/fromFloats do not exclude an exclusive bound that is an infinity: the empty ranges (+Inf, +Inf] and [-Inf, -Inf) are accepted and match the +Infinity / -Infinity documents instead of matching nothing

Bug LFV-313 · Area: facet / range, rangeonrange and facetset (exclusive-bound adjustment of double and float ranges: range.DoubleRange, rangeonrange.DoubleRange, facetset.DimRange.fromDoubles/fromFloats) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a facet range with an exclusive bound that is an infinity, for example new DoubleRange("&gt; +Inf", Double.POSITIVE_INFINITY, false, Double.POSITIVE_INFINITY, true), meaning 'value &gt; +Infinity', or the mirror [-Inf, -Inf) 'value &lt; -Infinity'. No double is greater than +Infinity, so you expect a range that matches nothing, rejected with IllegalArgumentException 'matches nothing' as the class does for other empty ranges and as LongRange and DimRange.fromLongs do for an exclusive Long.MAX_VALUE bound. Instead the constructor makes the bound inclusive with Math.nextUp (or Math.nextAfter / Math.nextDown for the max), which leaves an infinity unchanged, so the range becomes [+Inf, +Inf]: DoubleRangeFacetCounts counts and the drill-down query matches every +Infinity document, and the copies in rangeonrange.DoubleRange and DimRange.fromDoubles/fromFloats count the same documents.

## Expected vs actual

- Expected: You expect a range that matches nothing, rejected with IllegalArgumentException 'matches nothing' as the class does for other empty ranges and as LongRange and DimRange.fromLongs do for an exclusive Long.MAX_VALUE bound.
- Actual: The constructor makes the bound inclusive with Math.nextUp (or Math.nextAfter / Math.nextDown for the max), which leaves an infinity unchanged, so the range becomes [+Inf, +Inf]: DoubleRangeFacetCounts counts and the drill-down query matches every +Infinity document, and the copies in rangeonrange.DoubleRange and DimRange.fromDoubles/fromFloats count the same documents.

## Files

- [src/org/apache/lucene/facet/range/TestDoubleRangeExclusiveInfinity.java](src/org/apache/lucene/facet/range/TestDoubleRangeExclusiveInfinity.java) (JUnit test)

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
     execute --select-class org.apache.lucene.facet.range.TestDoubleRangeExclusiveInfinity
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
