# Weight#matches of Intervals.atLeast, or iterating the Matches it returns, throws IllegalStateException (AssertionError with -ea) for a matching document once an Intervals.or(...) source has run out of intervals, so default UnifiedHighlighter highlighting of such a query fails

Bug LFV-806 · Area: queries / intervals (Intervals.atLeast Matches path: MinimumShouldMatchIntervalsSource, CachingMatchesIterator) · Priority: medium · Impact: crash · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index 'a c b a' and ask Weight#matches, or the default UnifiedHighlighter, for Intervals.atLeast(2, term a, term b, or(term c, term d)); you expect the positions [0,1] [1,2] [2,3] that the query's own interval iterator returns and that the control atLeast(2, a, b, c) gives, and the snippet '&lt;b&gt;a&lt;/b&gt; &lt;b&gt;c&lt;/b&gt; &lt;b&gt;b&lt;/b&gt; &lt;b&gt;a&lt;/b&gt;'. Instead iterating the Matches throws IllegalStateException (AssertionError with -ea) from DisjunctionIntervalIterator.currentOrd and the whole highlight call fails, also for the query text 'fn:atLeast(2 a b fn:or(c d))': atLeast's match callback asks every source for its sub-matches, including the or source that has no interval left and that atLeast has already dropped. For some documents Weight#matches itself throws (atLeast(2, or(c, d), b, phrase(a, b)) on 'c a b').

## Expected vs actual

- Expected: You expect the positions [0,1] [1,2] [2,3] that the query's own interval iterator returns and that the control atLeast(2, a, b, c) gives, and the snippet '&lt;b&gt;a&lt;/b&gt; &lt;b&gt;c&lt;/b&gt; &lt;b&gt;b&lt;/b&gt; &lt;b&gt;a&lt;/b&gt;'.
- Actual: Iterating the Matches throws IllegalStateException (AssertionError with -ea) from DisjunctionIntervalIterator.currentOrd and the whole highlight call fails, also for the query text 'fn:atLeast(2 a b fn:or(c d))': atLeast's match callback asks every source for its sub-matches, including the or source that has no interval left and that atLeast has already dropped.

## Files

- [src/org/apache/lucene/queries/intervals/TestAtLeastMatchesExhaustedDisjunction.java](src/org/apache/lucene/queries/intervals/TestAtLeastMatchesExhaustedDisjunction.java) (JUnit test)
- [src/org/apache/lucene/queries/intervals/TestAtLeastMatchesReachability.java](src/org/apache/lucene/queries/intervals/TestAtLeastMatchesReachability.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:$LUCENE/lucene/highlighter/build/libs/lucene-highlighter-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:$LUCENE/lucene/queryparser/build/libs/lucene-queryparser-$V.jar
CP=$CP:$LUCENE/lucene/sandbox/build/libs/lucene-sandbox-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.intervals.TestAtLeastMatchesExhaustedDisjunction \
       --select-class org.apache.lucene.queries.intervals.TestAtLeastMatchesReachability
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
