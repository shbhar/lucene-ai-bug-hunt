# Grouping DoubleRangeFactory picks a value's bucket and computes that bucket's bounds with separately rounded arithmetic, so with a decimal width such as 0.1 the returned range can exclude the value and adjacent ranges leave gaps or overlap (DoubleRangeFactory(0, 0.1, 10).getRange(0.6) = DoubleRange(0.5, 0.6))

Bug LFV-412 · Area: grouping / range group selectors (DoubleRangeFactory, used by DoubleRangeGroupSelector) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You group documents by a double field with new DoubleRangeFactory(0, 0.1, 10), buckets 0.1 wide, and expect each group's DoubleRange label to contain the values grouped under it, because DoubleRange is documented as [min, max). Instead the value 0.6 is grouped under DoubleRange(0.5, 0.6), which excludes 0.6, and 1.7 under DoubleRange(1.7000000000000002, 1.8000000000000003); 27 of the 100 values 0.0, 0.1,..., 9.9 lie outside their own label, adjacent labels leave gaps (no label contains 0.6) or overlap, and a range query built from a label misses or picks up documents (values 0.55, 0.6, 0.65 form a group DoubleRange(0.5, 0.6) of 2 documents whose label matches 1). The cause is that getRange picks the bucket with Math.floor((value - min) / width) but computes its bounds separately as min + bucket * width and then that + width, and the three roundings disagree near boundaries; group membership itself stays consistent.

`new DoubleRangeFactory(0, 0.1, 10).getRange(0.6, null)` returns a range that does not contain 0.6.

## Expected vs actual

Expected: a range [min, max) that contains 0.6 (DoubleRange is documented as inclusive min, exclusive max). Actual: `DoubleRange(0.5, 0.6)`.

## Files

- [src/org/apache/lucene/search/grouping/TestDoubleRangeFactoryContainsValue.java](src/org/apache/lucene/search/grouping/TestDoubleRangeFactoryContainsValue.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/grouping/build/libs/lucene-grouping-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.grouping.TestDoubleRangeFactoryContainsValue
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
