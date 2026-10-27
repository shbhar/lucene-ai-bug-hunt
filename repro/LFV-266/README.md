# SpanWithinQuery's Weight#matches reports the contained (little) span's positions but the enclosing (big) span's offsets and sub-matches, so Matches-based highlighting (the default UnifiedHighlighter, MatchRegionRetriever) marks the enclosing terms and a field with offsets highlights differently from the same text indexed with positions only

Bug LFV-266 · Area: queries / span queries Matches API (SpanWeight#matches takes offsets and sub-matches from ContainSpans#collect for SpanWithinQuery; seen through the UnifiedHighlighter and MatchRegionRetriever) · Priority: medium · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index 'ab ac bc' in a field with offsets and ask Weight#matches for SpanWithinQuery(big = SpanNear([ab, bc], slop 1, in order), little = ac); you expect one match, 'ac' at position 1 with offsets [3,5) and sub-matches only inside it, which is what the equivalent Intervals.containedBy(ac, ordered(ab, bc)) reports. Instead the match says positions [1,1] but offsets [0,8) and lists ab@0 and bc@2 as sub-matches, because the offsets and sub-matches come from Spans#collect, which for SpanWithinQuery also collects the enclosing span. So the default UnifiedHighlighter marks '&lt;b&gt;ab&lt;/b&gt; &lt;b&gt;ac&lt;/b&gt; &lt;b&gt;bc&lt;/b&gt;' instead of 'ab &lt;b&gt;ac&lt;/b&gt; bc', and MatchRegionRetriever returns [0,8) on a field with offsets but [3,5) on the same text indexed with positions only; hits and scores are unaffected.

## Expected vs actual

- Expected: You expect one match, 'ac' at position 1 with offsets [3,5) and sub-matches only inside it, which is what the equivalent Intervals.containedBy(ac, ordered(ab, bc)) reports.
- Actual: The match says positions [1,1] but offsets [0,8) and lists ab@0 and bc@2 as sub-matches, because the offsets and sub-matches come from Spans#collect, which for SpanWithinQuery also collects the enclosing span.

## Files

- [src/org/apache/lucene/queries/spans/TestSpanWithinMatchesOffsets.java](src/org/apache/lucene/queries/spans/TestSpanWithinMatchesOffsets.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.spans.TestSpanWithinMatchesOffsets
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
