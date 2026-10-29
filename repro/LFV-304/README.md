# DisjunctionMaxQuery and LatLonPoint.newDistanceFeatureQuery accept a NaN tie-breaker or pivot distance (the pivot also +Infinity) because their range checks are false for NaN, so hits score NaN and a NaN query is not equal to itself

Bug LFV-304 · Area: core / search and document: query parameter validation (DisjunctionMaxQuery, LatLonPoint distance feature query) · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a DisjunctionMaxQuery with tieBreakerMultiplier NaN (for example a computed value, or the XML query parser text tieBreaker='NaN'), or a LatLonPoint.newDistanceFeatureQuery with pivotDistance NaN or +Infinity, and expect the IllegalArgumentException that both constructors already throw for every other out-of-range value ('tieBreakerMultiplier must be in [0, 1]', 'pivotDistance must be &gt; 0'). Instead the value is accepted, because the checks are written as 'x &lt; 0 || x &gt; 1' and 'x &lt;= 0', which are false for NaN (and '&lt;= 0' is false for +Infinity): on main the hits come back with NaN scores, although the distance feature javadoc promises scores in [0, weight], and for a NaN value q.equals(q) is false because equals compares the field with ==. Older releases are worse: with a NaN tie-breaker, 8.0.0 and 8.11.4 return no top-k hits while count finds them, and 9.12.3 with assertions off returns a sentinel ScoreDoc (doc 2147483647, score -Infinity) in place of a hit.

The test shows that `new DisjunctionMaxQuery(List.of(), Float.NaN)` and `LatLonPoint.newDistanceFeatureQuery("f", 1, 0, 0, Double.NaN)` pass their range checks (`x < 0 || x > 1` and `x <= 0` are false for NaN) (`TestQueryRangeChecksAdmitNaN`, one method per constructor).

## Expected vs actual

Expected `IllegalArgumentException` ('tieBreakerMultiplier must be in [0, 1]' / 'pivotDistance must be &gt; 0', as for every other out-of-range value); actual: both are accepted (`Expected exception IllegalArgumentException but no exception was thrown`, 2 of 2), with -ea and -da.

## Files

- [src/org/apache/lucene/search/TestQueryRangeChecksAdmitNaN.java](src/org/apache/lucene/search/TestQueryRangeChecksAdmitNaN.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 2 test(s) run, 2 failed.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestQueryRangeChecksAdmitNaN
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/query-object-contracts/](../../models/query-object-contracts/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
