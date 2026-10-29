# Grouping DoubleRangeFactory gives the below-min group the lower bound Double.MIN_VALUE (4.9E-324, the smallest positive double) instead of the most negative value, so that group's DoubleRange label does not contain its own members

Bug LFV-411 · Area: grouping / range group selectors (DoubleRangeFactory, used by DoubleRangeGroupSelector) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You group documents by a double field with new DoubleRangeFactory(-10, 5, 10), meaning buckets 5 wide from -10 to 10 and every value below -10 in one bucket, and expect the bucket returned for -20 to be a [min, max) range that contains -20, the way LongRangeFactory returns [Long.MIN_VALUE, -10). Instead DoubleRangeFactory returns DoubleRange(4.9E-324, -10.0): it uses Double.MIN_VALUE as the lower end, and Double.MIN_VALUE is the smallest positive double, not the most negative one, so the range is inverted and holds none of its members (with min &gt; 0 it leaves out 0.0 and every negative member). Each group still holds the right documents, but its reported bounds are wrong, and a range query built from them, the way Lucene's own selector tests rebuild a group, finds none or only some of the group's documents.

The test shows that `new DoubleRangeFactory(-10, 5, 10).getRange(-20, null)` gives the below-min bucket the lower bound `Double.MIN_VALUE` (the smallest positive double).

## Expected vs actual

Expected: a [min, -10.0) range that contains -20; actual (-ea and -da): the inverted, empty `DoubleRange(4.9E-324, -10.0)` ("DoubleRange(4.9E-324, -10.0) should contain -20.0").

## Files

- [src/org/apache/lucene/search/grouping/TestDoubleRangeFactoryBelowMin.java](src/org/apache/lucene/search/grouping/TestDoubleRangeFactoryBelowMin.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.grouping.TestDoubleRangeFactoryBelowMin
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
