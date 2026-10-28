# Classic Highlighter counts the unused positions before a MultiPhraseQuery's first position as gaps, so a phrase that starts at position 1 or later (a leading stop word plus a synonym) is highlighted with extra slop and non-matching pairs get marked

Bug LFV-829 · Area: highlighter / original Highlighter, QueryScorer and WeightedSpanTermExtractor (MultiPhraseQuery-to-span conversion) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-829.html](../../bugs/LFV-829.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You search for the quoted phrase "the dogs bark" with an analyzer that removes the stop word 'the' and adds the synonym dog for dogs (QueryParser and QueryBuilder build a MultiPhraseQuery [dogs|dog] at position 1, bark at position 2, slop 0), and highlight the text 'dogs often bark but my dogs bark' with Highlighter and QueryScorer, expecting 'dogs often bark but my &lt;B&gt;dogs&lt;/B&gt; &lt;B&gt;bark&lt;/B&gt;' (Weight#matches reports only positions 5-6, and the same query starting at position 0, or a PhraseQuery at positions 1 and 2, is highlighted that way). Instead you get '&lt;B&gt;dogs&lt;/B&gt; often &lt;B&gt;bark&lt;/B&gt; but my &lt;B&gt;dogs&lt;/B&gt; &lt;B&gt;bark&lt;/B&gt;', with no error. WeightedSpanTermExtractor counts every empty slot from index 0 up to the last position as a position gap, including the unused slot 0 below the first position, so the phrase becomes an ordered SpanNearQuery with slop 1 instead of 0 (one extra unit per leading slot, also for sloppy phrases), while its PhraseQuery branch measures gaps from the first position.

## Expected vs actual

- Expected: `dogs often bark but my <B>dogs</B> <B>bark</B>`, as for the same query at positions 0,1 and for the `PhraseQuery` at 1,2.
- Actual: `<B>dogs</B> often <B>bark</B> but my <B>dogs</B> <B>bark</B>`: the non-matching pair one word apart is marked too; no error.

## Files

- [src/org/apache/lucene/search/highlight/TestHighlighterMultiPhraseLeadingPosition.java](src/org/apache/lucene/search/highlight/TestHighlighterMultiPhraseLeadingPosition.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.highlight.TestHighlighterMultiPhraseLeadingPosition
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestHighlighterMultiPhraseLeadingPosition.java](TestHighlighterMultiPhraseLeadingPosition.java)
- [TestMultiPhraseFirstPositionAboveZero.java](TestMultiPhraseFirstPositionAboveZero.java)
- [TestMpqOffsetInvariance.java](TestMpqOffsetInvariance.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
