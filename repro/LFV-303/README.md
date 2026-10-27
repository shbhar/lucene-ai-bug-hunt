# Multi-valued range facet counts drop matches when a document's values do not arrive in ascending order (custom MultiLongValuesSource/MultiDoubleValuesSource, or raw-bit doubles with two or more negative values): LongRangeCounter.addMultiValued assumes sorted values

Bug LFV-303 · Area: facet / range facet counts (LongRangeCounter multi-valued path, exclusive and overlapping counters) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-303.html](../../bugs/LFV-303.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You compute range facet counts over a multi-valued source, for example the distances from the user to each of a document's locations computed by your own MultiDoubleValuesSource, or doubles stored as raw bits in a SortedNumeric field and read with fromDoubleField or the DoubleRangeFacetCounts(String,...) constructor. You expect each range to count every document that has at least one value in it, which is exactly what the range's own drill-down query finds. Instead, when a document's values arrive in descending order (a custom source in its natural order, or raw-bit doubles whenever the document has two or more negative values), the counter skips every later value that sorts below an interval it already counted for that document, so a document with distances {3.0, 0.5} is missing from '&lt; 1 km' and '&lt; 2 km'.

## Expected vs actual

- Expected: Every range counts the document, as its drill-down query does: "&lt; 1 km", "&lt; 2 km", "&lt; 5 km" = 1, 1, 1 (and `[1, 1]` in the long and raw-bit tests).
- Actual: "&lt; 1 km", "&lt; 2 km", "&lt; 5 km" = 0, 0, 1; the long and raw-bit tests get `[0, 1]`, and `{500, 5}` gets `[0, 0]` with total 0 instead of 1.

## Files

- [src/org/apache/lucene/facet/range/TestRangeFacetUnsortedMultiValues.java](src/org/apache/lucene/facet/range/TestRangeFacetUnsortedMultiValues.java) (JUnit test)

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
     execute --select-class org.apache.lucene.facet.range.TestRangeFacetUnsortedMultiValues
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestRangeFacetMultiValuedOrder.java](TestRangeFacetMultiValuedOrder.java)
- [TestRangeFacetMultiValuedOrderRandom.java](TestRangeFacetMultiValuedOrderRandom.java)
- [TestRangeFacetUnsortedMultiValues.java](TestRangeFacetUnsortedMultiValues.java)
- [RangeOrderScaleProbe.java](RangeOrderScaleProbe.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
