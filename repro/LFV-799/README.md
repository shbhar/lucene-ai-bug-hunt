# LongRangeOnRangeFacetCounts and DoubleRangeOnRangeFacetCounts throw an internal ArrayIndexOutOfBoundsException from every public constructor when given no ranges, instead of returning an empty result like the classic range facet counters or rejecting the empty list with an IllegalArgumentException

Bug LFV-799 · Area: facet / range-on-range facet counts (LongRangeOnRangeFacetCounts and DoubleRangeOnRangeFacetCounts constructors: getEncodedRanges reads ranges[0]) · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You count range-on-range facets with new LongRangeOnRangeFacetCounts(field, hits, QueryType.INTERSECTS) (or the Double variant) and pass no ranges, a call the varargs signature accepts and no javadoc forbids, and expect an empty FacetResult, as the classic LongRangeFacetCounts returns for the same empty request, or at least an IllegalArgumentException that rejects the empty list. Instead the constructor throws 'ArrayIndexOutOfBoundsException: Index 0 out of bounds for length 0', because its private helper getEncodedRanges sizes the encoded array from ranges[0].dims without checking that the list is non-empty.

The test shows that `LongRangeOnRangeFacetCounts` with no ranges throws an internal `ArrayIndexOutOfBoundsException` from its constructor (`getEncodedRanges` reads `ranges[0]`).

## Expected vs actual

Expected: an empty result (as the classic range counters return) or an `IllegalArgumentException`; actual: `ArrayIndexOutOfBoundsException: Index 0 out of bounds for length 0`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/facet/rangeonrange/TestRangeOnRangeFacetCountsNoRanges.java](src/org/apache/lucene/facet/rangeonrange/TestRangeOnRangeFacetCountsNoRanges.java) (JUnit test)

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
     execute --select-class org.apache.lucene.facet.rangeonrange.TestRangeOnRangeFacetCountsNoRanges
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
