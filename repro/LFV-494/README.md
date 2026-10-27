# UnifiedHighlighter with LengthGoalBreakIterator on a multi-valued field: a passage in a later value starts at the U+0000 value separator

Bug LFV-494 · Area: highlighter / UnifiedHighlighter break iterators (LengthGoalBreakIterator#preceding under the SplittingBreakIterator that UnifiedHighlighter puts around every field's iterator, multi-valued fields) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You highlight a field with two stored values, 'First value here.' and 'Second value here.', using UnifiedHighlighter.builder(...).withBreakIterator( -&gt; LengthGoalBreakIterator.createMinLength(sentence iterator, 10, 0.5f)), and expect the same snippet as with the plain sentence iterator, because each value is already one sentence longer than the goal: '&lt;b&gt;Second&lt;/b&gt; value here.' for the query 'second'. Instead the passage starts one character early, on the internal U+0000 value separator ('\u0000&lt;b&gt;Second&lt;/b&gt; value here.'), and for 'first second' the two values come out joined by U+0000 instead of '... ' ('&lt;b&gt;First&lt;/b&gt; value here.\u0000&lt;b&gt;Second&lt;/b&gt; value here.'). The cause is LengthGoalBreakIterator.preceding answering DONE (-1) for an offset inside the value, which the SplittingBreakIterator that UnifiedHighlighter always wraps around the iterator turns into the separator's index.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The passage starts one character early, on the internal U+0000 value separator ('\u0000&lt;b&gt;Second&lt;/b&gt; value here.'), and for 'first second' the two values come out joined by U+0000 instead of '... ' ('&lt;b&gt;First&lt;/b&gt; value here.\u0000&lt;b&gt;Second&lt;/b&gt; value here.').

## Files

- [src/org/apache/lucene/search/uhighlight/TestLengthGoalMultiValueSeparator.java](src/org/apache/lucene/search/uhighlight/TestLengthGoalMultiValueSeparator.java) (JUnit test)

## Run it

- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
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

# run 1: assertions on, seed C0FFEE150
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=C0FFEE150 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.uhighlight.TestLengthGoalMultiValueSeparator
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
