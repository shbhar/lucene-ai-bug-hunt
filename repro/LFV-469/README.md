# Intervals.atLeast accepts minShouldMatch 0 (also from query text such as fn:atLeast(0 a b)), and the query then throws NullPointerException or ArrayIndexOutOfBoundsException on every non-empty segment it searches instead of being rejected as an invalid argument

Bug LFV-469 · Area: queries / intervals (Intervals.atLeast: MinimumShouldMatchIntervalsSource); queryparser / flexible StandardQueryParser fn:atLeast · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build Intervals.atLeast(0, Intervals.term("a"), Intervals.term("b")), or parse 'fn:atLeast(0 a b)' with the flexible StandardQueryParser, and search an index that contains 'a b c'. You expect the 0 to be rejected with an IllegalArgumentException when the query is built (the same factory already handles a count equal to or larger than the number of sources), or else a search that runs. Instead the query is accepted and every search with it throws: NullPointerException in MinimumShouldMatchIntervalIterator.nextInterval, because top of its empty, capacity-0 queue is null, or ArrayIndexOutOfBoundsException from an empty DisiPriorityQueue on a segment that holds none of the terms.

## Expected vs actual

- Expected: You expect the 0 to be rejected with an IllegalArgumentException when the query is built (the same factory already handles a count equal to or larger than the number of sources), or else a search that runs.
- Actual: The query is accepted and every search with it throws: NullPointerException in MinimumShouldMatchIntervalIterator.nextInterval, because top of its empty, capacity-0 queue is null, or ArrayIndexOutOfBoundsException from an empty DisiPriorityQueue on a segment that holds none of the terms.

## Files

- [src/org/apache/lucene/queries/intervals/TestAtLeastZeroMinShouldMatch.java](src/org/apache/lucene/queries/intervals/TestAtLeastZeroMinShouldMatch.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:$LUCENE/lucene/queryparser/build/libs/lucene-queryparser-$V.jar
CP=$CP:$LUCENE/lucene/sandbox/build/libs/lucene-sandbox-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.intervals.TestAtLeastZeroMinShouldMatch
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
