# Highlighter with the default QueryScorer returns no fragment for a matching PhraseQuery or MultiPhraseQuery that has a position hole (for example a removed stop word) and a slop within the number of holes of Integer.MAX_VALUE: WeightedSpanTermExtractor converts it to a SpanNearQuery with the plain int sum slop + positionGaps, which wraps to about Integer.MIN_VALUE, so the span query matches nothing

Bug LFV-397 · Area: highlighter / original Highlighter, QueryScorer and WeightedSpanTermExtractor (phrase-to-span conversion) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-397.html](../../bugs/LFV-397.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You analyse 'the quick brown fox jumps' with a stop-word analyzer, search for the phrase "quick the fox" with slop Integer.MAX_VALUE (for example from the user text "quick the fox"~3000000000, which the classic QueryParser turns into "quick ? fox"~2147483647; the query matches), and highlight the hit with Highlighter and QueryScorer, expecting 'the &lt;B&gt;quick&lt;/B&gt; brown &lt;B&gt;fox&lt;/B&gt; jumps', which slop Integer.MAX_VALUE - 1 or the same phrase without the hole gives. Instead getBestFragment returns null, with no error: WeightedSpanTermExtractor rewrites the phrase as new SpanNearQuery(clauses, slop + positionGaps, inorder) with a plain int sum, the removed stop word counts as one position gap, the sum wraps to -2147483648, and a span query with that slop accepts no match, so no term is marked. MultiPhraseQuery has the same sum, and the UnifiedHighlighter with weight matches turned off reaches it through PhraseHelper and returns the passage with no highlight.

The test shows that Highlighter with QueryScorer returns no fragment for a matching PhraseQuery with a position hole and slop Integer.MAX_VALUE, because WeightedSpanTermExtractor's int sum slop + positionGaps wraps negative.

## Expected vs actual

Expected `the <B>quick</B> brown <B>fox</B> jumps`; actual `null` (`expected:<the <B>quick</B> brown <B>fox</B> jumps> but was:<null>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/search/highlight/TestHighlighterPhraseSlopOverflow.java](src/org/apache/lucene/search/highlight/TestHighlighterPhraseSlopOverflow.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/highlighter/build/libs/lucene-highlighter-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.highlight.TestHighlighterPhraseSlopOverflow
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/overflow-at-scale-2/](../../models/overflow-at-scale-2/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
