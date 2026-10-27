# Intervals.term(term, payloadFilter) match iterators throw UnsupportedOperationException from getQuery, so Weight#matches of ordered/unordered/maxgaps/atLeast over such a term, and Matches-based (default UnifiedHighlighter) highlighting of most positional combinations, throw for every matching document

Bug LFV-463 · Area: queries / intervals (PayloadFilteredTermIntervalsSource Matches API; UnifiedHighlighter WEIGHT_MATCHES) · Priority: low · Impact: crash · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

Index a field with payloads (for example 'big|adj dog|noun' through DelimitedPayloadTokenFilter), search it with Intervals.ordered(Intervals.term("big"), Intervals.term("dog", payloadFilter)), then ask Weight#matches for the match positions of a hit or highlight the hit with the UnifiedHighlighter. You expect the positions and a highlighted snippet, as with the plain Intervals.term("dog"). Instead Weight#matches throws UnsupportedOperationException for every matching document (ordered, unordered, maxgaps, atLeast), and highlighting throws for those and also for phrase, containing and containedBy, because the payload-filtered term's match iterator throws from getQuery, which MatchesIterator#getQuery says returns a TermQuery for the current match; search and count still return the right hits.

## Expected vs actual

- Expected: You expect the positions and a highlighted snippet, as with the plain Intervals.term("dog").
- Actual: Weight#matches throws UnsupportedOperationException for every matching document (ordered, unordered, maxgaps, atLeast), and highlighting throws for those and also for phrase, containing and containedBy, because the payload-filtered term's match iterator throws from getQuery, which MatchesIterator#getQuery says returns a TermQuery for the current match; search and count still return the right hits.

## Files

- [src/org/apache/lucene/queries/intervals/TestPayloadFilteredIntervalsMatchesGetQuery.java](src/org/apache/lucene/queries/intervals/TestPayloadFilteredIntervalsMatchesGetQuery.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/highlighter/build/libs/lucene-highlighter-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.intervals.TestPayloadFilteredIntervalsMatchesGetQuery
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
