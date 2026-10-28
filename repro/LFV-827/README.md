# Classic Highlighter turns the position holes of an exact PhraseQuery or MultiPhraseQuery (for example from removed stop words) into SpanNearQuery slop, so it highlights nearby pairs that do not match and can miss the occurrence that does

Bug LFV-827 · Area: highlighter / original Highlighter, QueryScorer and WeightedSpanTermExtractor (phrase-to-span conversion) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index 'rock roll rock and roll' with a stop-word analyzer, search for the phrase "rock and roll" (QueryParser and QueryBuilder make it rock at position 0 and roll at position 2, slop 0, and Weight#matches reports the only match at positions 2-4), and highlight the hit with Highlighter and QueryScorer, expecting 'rock roll &lt;B&gt;rock&lt;/B&gt; and &lt;B&gt;roll&lt;/B&gt;'. Instead you get '&lt;B&gt;rock&lt;/B&gt; &lt;B&gt;roll&lt;/B&gt; &lt;B&gt;rock&lt;/B&gt; and &lt;B&gt;roll&lt;/B&gt;', and on 'rock roll roll', which matches only as the first rock plus the last roll, you get '&lt;B&gt;rock&lt;/B&gt; &lt;B&gt;roll&lt;/B&gt; roll', with the matching last word unmarked. WeightedSpanTermExtractor turns the phrase's one hole into slop 1 of an ordered SpanNearQuery, which accepts 0 or 1 words between the terms instead of exactly 1 and keeps only the shortest span per start; MultiPhraseQuery has the same conversion, and the UnifiedHighlighter with weight matches turned off reaches it through PhraseHelper.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: You get '&lt;B&gt;rock&lt;/B&gt; &lt;B&gt;roll&lt;/B&gt; &lt;B&gt;rock&lt;/B&gt; and &lt;B&gt;roll&lt;/B&gt;', and on 'rock roll roll', which matches only as the first rock plus the last roll, you get '&lt;B&gt;rock&lt;/B&gt; &lt;B&gt;roll&lt;/B&gt; roll', with the matching last word unmarked.

## Files

- [src/org/apache/lucene/search/highlight/TestHighlighterPhraseHolesAsSlop.java](src/org/apache/lucene/search/highlight/TestHighlighterPhraseHolesAsSlop.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/highlighter/build/libs/lucene-highlighter-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.highlight.TestHighlighterPhraseHolesAsSlop
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
