# UnifiedHighlighter from the default builder can print its internal value separator U+0000 inside a snippet of a multi-valued field, when a phrase match that runs from the end of one stored value into the next opens the passage (run: values 'one bravo three' and 'four bravo six', position gap 3, phrase "three four" with slop 3: 'one bravo &lt;b&gt;three\u0000four&lt;/b&gt;')

Finding LFV-1561 · Area: highlighter / UnifiedHighlighter passage bounds on multi-valued fields (FieldHighlighter.highlightOffsetsEnums: the end of a passage that a match opens) · Priority: low · Impact: minor · Novelty: known outside Lucene

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You store two values in one text field, 'one bravo three' and 'four bravo six', and highlight the phrase query "three four" with a UnifiedHighlighter from the default builder; the phrase matches across the two values because its slop of 3 equals the analyzer's position gap (run also with StandardAnalyzer, whose gap is 0, and an exact two-word phrase across the values 'john smith' and 'jones mary'). You expect highlighted text from the stored values, without an internal separator, as in Lucene's own test of the same document, where another match opens the passage and the highlight of the phrase ends at the value end ('one &lt;b&gt;bravo&lt;/b&gt; &lt;b&gt;three&lt;/b&gt;... four &lt;b&gt;bravo&lt;/b&gt; six'). Instead the snippet is 'one bravo &lt;b&gt;three\u0000four&lt;/b&gt;' (in the test of the repro folder, which is the StandardAnalyzer run with the values 'john smith' and 'jones mary': 'john &lt;b&gt;smith\u0000jones&lt;/b&gt;'): the highlighter joins the values with an internal U+0000, and when the match that opens a passage itself crosses that separator, FieldHighlighter stretches the passage at least to the end of the match, so the default formatter prints the separator inside the highlight tags and, for this document, stops in the middle of the second value. The search itself is not involved (the hit is found before highlighting starts); not new in 11.0 (the 10.6.0 release gives the same snippet); known outside Lucene (an Elasticsearch issue of 2025 shows this output, and that project replaced the separator in its own formatter).

## Expected vs actual

- Expected: You expect highlighted text from the stored values, without an internal separator, as in Lucene's own test of the same document, where another match opens the passage and the highlight of the phrase ends at the value end ('one &lt;b&gt;bravo&lt;/b&gt; &lt;b&gt;three&lt;/b&gt;... four &lt;b&gt;bravo&lt;/b&gt; six').
- Actual: The snippet is 'one bravo &lt;b&gt;three\u0000four&lt;/b&gt;' (in the test of the repro folder, which is the StandardAnalyzer run with the values 'john smith' and 'jones mary': 'john &lt;b&gt;smith\u0000jones&lt;/b&gt;'): the highlighter joins the values with an internal U+0000, and when the match that opens a passage itself crosses that separator, FieldHighlighter stretches the passage at least to the end of the match, so the default formatter prints the separator inside the highlight tags and, for this document, stops in the middle of the second value.

## Files

- [src/org/apache/lucene/search/uhighlight/TestUHCrossValuePhraseSeparator.java](src/org/apache/lucene/search/uhighlight/TestUHCrossValuePhraseSeparator.java) (JUnit test)

## Run it

- Tested on Lucene main 82cf7465bb8f (a commit of 2026-11-26): still reproduces.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual, or names the exception that was thrown).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/highlighter/build/libs/lucene-highlighter-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed 67B0892
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=67B0892 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.uhighlight.TestUHCrossValuePhraseSeparator
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on branch_11x the build takes the default from the seed).

Formal models and scripts this finding's part of the hunt used: [../../models/unified-highlighter-cross-value-phrase-passage-end/](../../models/unified-highlighter-cross-value-phrase-passage-end/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
