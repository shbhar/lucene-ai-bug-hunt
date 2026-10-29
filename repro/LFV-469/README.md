# Intervals.atLeast accepts minShouldMatch 0 (also from query text such as fn:atLeast(0 a b)), and the query then throws NullPointerException or ArrayIndexOutOfBoundsException on every non-empty segment it searches instead of being rejected as an invalid argument

Bug LFV-469 · Area: queries / intervals (Intervals.atLeast: MinimumShouldMatchIntervalsSource); queryparser / flexible StandardQueryParser fn:atLeast · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build Intervals.atLeast(0, Intervals.term("a"), Intervals.term("b")), or parse 'fn:atLeast(0 a b)' with the flexible StandardQueryParser, and search an index that contains 'a b c'. You expect the 0 to be rejected with an IllegalArgumentException when the query is built (the same factory already handles a count equal to or larger than the number of sources), or else a search that runs. Instead the query is accepted and every search with it throws: NullPointerException in MinimumShouldMatchIntervalIterator.nextInterval, because top of its empty, capacity-0 queue is null, or ArrayIndexOutOfBoundsException from an empty DisiPriorityQueue on a segment that holds none of the terms.

The test shows that `Intervals.atLeast(0, term("a"), term("b"))` is accepted, and that counting it over a one-document index ("a b c") throws an internal NullPointerException.

## Expected vs actual

Expected: 0 rejected when the source is built (IllegalArgumentException), or a search that runs. Actual: `NullPointerException` because `PriorityQueue.top()` is null, at MinimumShouldMatchIntervalsSource.java:218, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/queries/intervals/TestIntervalsAtLeastZero.java](src/org/apache/lucene/queries/intervals/TestIntervalsAtLeastZero.java) (JUnit test)

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
     execute --select-class org.apache.lucene.queries.intervals.TestIntervalsAtLeastZero
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
