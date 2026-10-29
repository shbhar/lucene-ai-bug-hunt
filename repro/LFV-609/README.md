# matchhighlight PassageFormatter closes crossing markers in opening order instead of innermost first, so per-marker end strings come out mis-nested (e.g. '&lt;a&gt;one &lt;b&gt;two&lt;/a&gt;&lt;/b&gt;&lt;b&gt; three&lt;/b&gt;' instead of '&lt;a&gt;one &lt;b&gt;two&lt;/b&gt;&lt;/a&gt;&lt;b&gt; three&lt;/b&gt;')

Bug LFV-609 · Area: highlighter / matchhighlight: PassageFormatter multi-marker formatting (crossing markers with per-marker end strings; also end to end through MatchHighlighter, FieldValueHighlighters.highlighted and MatchHighlighter.QueryOffsetRange) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You format the passage 'one two three' with two crossing markers, [0,7) written as &lt;a&gt;...&lt;/a&gt; and [4,13) as &lt;b&gt;...&lt;/b&gt;, using PassageFormatter's Function constructor, or you highlight the crossing phrase queries 'one two' and 'two three' with MatchHighlighter.highlight(topDocs, q1, q2) and one tag per query. You expect well-nested output, '&lt;a&gt;one &lt;b&gt;two&lt;/b&gt;&lt;/a&gt;&lt;b&gt; three&lt;/b&gt;', because when a marker ends the formatter closes and re-opens the markers opened after it precisely to resolve the overlap into nested markup (Passage javadoc: markers 'can be overlapping or nested'). Instead it closes those markers in opening order rather than innermost first and returns '&lt;a&gt;one &lt;b&gt;two&lt;/a&gt;&lt;/b&gt;&lt;b&gt; three&lt;/b&gt;' (end to end '&lt;q1&gt;one &lt;q2&gt;two&lt;/q1&gt;&lt;/q2&gt;&lt;q2&gt; three&lt;/q2&gt;'), which an XML parser rejects.

The test shows that matchhighlight PassageFormatter, with per-marker end strings, closes two crossing markers in opening order instead of innermost first, so the end tags come out mis-nested.

## Expected vs actual

For 'foo bar baz' with [0,7) as &lt;a&gt; and [4,11) as &lt;b&gt;: expected `<a>foo <b>bar</b></a><b> baz</b>`; actual `<a>foo <b>bar</a></b><b> baz</b>`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/search/matchhighlight/TestPassageFormatterCrossingMarkers.java](src/org/apache/lucene/search/matchhighlight/TestPassageFormatterCrossingMarkers.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.matchhighlight.TestPassageFormatterCrossingMarkers
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
