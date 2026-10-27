# DoubleRangeFacetCounts orders -0.0 below +0.0 while DoubleRange.accept and getQuery treat the two zeros as equal: a document holding a zero of the opposite sign at an inclusive zero bound (for example -0.0 in [0, 1]) is matched by the range's own drill-down but not counted, and the accepted range [+0.0, -0.0] makes counting throw

Bug LFV-310 · Area: facet / range facet counts (DoubleRange bounds and values converted to sortable longs in DoubleRangeFacetCounts, against DoubleRange.accept and getQuery) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You count range facets over a double field with DoubleRangeFacetCounts and a range that starts at an inclusive 0.0, such as new DoubleRange("[0, 1]", 0.0, true, 1.0, true), and some documents hold -0.0 (Java's negative zero, which ordinary arithmetic such as Math.ceil(-0.25) or -1 * 0.0 produces). You expect the count to include every document the range accepts, because DoubleRange.accept and the range's own drill-down query getQuery compare with primitive &gt;= and &lt;=, treat -0.0 as equal to 0.0, and match those documents. Instead the counter converts bounds and values with NumericUtils.doubleToSortableLong, where -0.0 sorts below +0.0, so those documents are silently left out of the count (likewise +0.0 at an inclusive -0.0 upper bound), and the range [+0.0, -0.0], which the DoubleRange constructor accepts, makes DoubleRangeFacetCounts throw IllegalArgumentException 'matches nothing'.

## Expected vs actual

- Expected: You expect the count to include every document the range accepts, because DoubleRange.accept and the range's own drill-down query getQuery compare with primitive &gt;= and &lt;=, treat -0.0 as equal to 0.0, and match those documents.
- Actual: The counter converts bounds and values with NumericUtils.doubleToSortableLong, where -0.0 sorts below +0.0, so those documents are silently left out of the count (likewise +0.0 at an inclusive -0.0 upper bound), and the range [+0.0, -0.0], which the DoubleRange constructor accepts, makes DoubleRangeFacetCounts throw IllegalArgumentException 'matches nothing'.

## Files

- [src/org/apache/lucene/facet/range/TestDoubleRangeSignedZero.java](src/org/apache/lucene/facet/range/TestDoubleRangeSignedZero.java) (JUnit test)

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
     execute --select-class org.apache.lucene.facet.range.TestDoubleRangeSignedZero
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
