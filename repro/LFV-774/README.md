# matchhighlight PassageSelector can cut a passage or a truncated match between the two halves of a supplementary character, so FieldValueHighlighters.maxLeadingCharacters and highlighted return a snippet with an unpaired surrogate

Bug LFV-774 · Area: highlighter / matchhighlight: PassageSelector passage windows and long-match truncation, BreakIteratorShrinkingAdjuster (through FieldValueHighlighters.maxLeadingCharacters and highlighted) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You highlight a stored value with matchhighlight's FieldValueHighlighters, for example maxLeadingCharacters(5, '...', fields) on a value that starts with the word BOLD written in mathematical bold capitals (U+1D401 U+1D40E U+1D40B U+1D403, two UTF-16 units each), or highlighted(5,...) with a term query that matches that word. You expect a snippet of whole characters within the 5-unit budget, for example the first two letters and '...', just as the ASCII value 'boldtext' is cut inside the word but between characters ('boldt...'). Instead the snippet ends with the first half of the third letter ('\uD835\uDC01\uD835\uDC0E\uD835...' and '&gt;\uD835\uDC01\uD835\uDC0E\uD835&lt;...'), because PassageSelector cuts windows and long matches at a raw UTF-16 offset and BreakIteratorShrinkingAdjuster keeps that raw edge when no word boundary fits in the window.

The test shows that the default matchhighlight PassageSelector (as used by `FieldValueHighlighters.maxLeadingCharacters(5,...)`) ends a 5-unit passage between the two halves of a supplementary character, so the snippet carries an unpaired high surrogate.

## Expected vs actual

Expected a passage ending on a code point boundary; actual `[[from=0, to=5], markers=[]]`, offset 5 inside U+1D40B (-ea and -da).

## Files

- [src/org/apache/lucene/search/matchhighlight/TestMatchHighlightWindowSurrogatePair.java](src/org/apache/lucene/search/matchhighlight/TestMatchHighlightWindowSurrogatePair.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.matchhighlight.TestMatchHighlightWindowSurrogatePair
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
