# Multi-term interval sources (Intervals.prefix, wildcard, regexp, fuzzyTerm, range, multiterm) skip the has-positions check that Intervals.term performs, so positional interval queries over them on a field indexed without positions silently return wrong hits (usually none) instead of throwing the documented exception

Bug LFV-604 · Area: queries / intervals (MultiTermIntervalsSource behind Intervals.prefix, wildcard, regexp, range, fuzzyTerm and multiterm; IntervalQuery search and Weight#matches) · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index 'alpha beta' into a field with IndexOptions.DOCS_AND_FREQS (no positions) and search IntervalQuery("body", Intervals.ordered(Intervals.prefix("alph"), Intervals.prefix("bet"))). You expect IllegalArgumentException ('... because it has no indexed positions'), because the IndexOptions javadoc says positional queries on such a field throw and the same query written with Intervals.term("alpha") and Intervals.term("beta") does throw. Instead MultiTermIntervalsSource never checks Terms#hasPositions, runs the interval logic on the position -1 that the postings return, and silently reports 0 hits (1 of 2 on a MultiReader that also holds a positioned copy of the document); wildcard, regexp, fuzzyTerm, range and multiterm leaves behave the same way.

The test shows that `IntervalQuery("body", Intervals.ordered(Intervals.prefix("alph"), Intervals.prefix("bet")))` on a `DOCS_AND_FREQS` field (one doc, `alpha beta`) never checks for indexed positions.

## Expected vs actual

Expected: `IllegalArgumentException` (no indexed positions), as `Intervals.term` throws and the `IndexOptions` javadoc documents. Actual: no exception, 0 hits.

## Files

- [src/org/apache/lucene/queries/intervals/TestMultiTermIntervalsWithoutPositions.java](src/org/apache/lucene/queries/intervals/TestMultiTermIntervalsWithoutPositions.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.intervals.TestMultiTermIntervalsWithoutPositions
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
