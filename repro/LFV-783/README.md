# UnifiedHighlighter with postings or term-vector offsets highlights the wrong characters, or drops the match, in the second and later values of a multi-valued field when the analyzer's offset gap is not 1

Bug LFV-783 · Area: highlighter / UnifiedHighlighter (multi-valued content: postings and term-vector offset sources vs Analyzer#getOffsetGap; LimitedStoredFieldVisitor value join) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a field with two values, 'alpha one' and 'beta two', with an analyzer whose offset gap is 3 (for example CustomAnalyzer.builder.withOffsetGap(3)) and offsets in the postings or term vectors, then highlight the hits of body:beta and body:two with the UnifiedHighlighter; you expect '&lt;b&gt;beta&lt;/b&gt; two' and 'beta &lt;b&gt;two&lt;/b&gt;', as with the default gap of 1. Instead you get 'be&lt;b&gt;ta t&lt;/b&gt;wo' and the unhighlighted 'alpha one', because indexing put the second value 3 characters after the first while the highlighter rebuilds the text with a single separator character between values, so every offset in the second value points 2 characters too far. The re-analysis offset source rejects the same analyzer with an IllegalArgumentException; the postings and term-vector sources accept it and return wrong snippets silently.

## Expected vs actual

- Expected: You expect '&lt;b&gt;beta&lt;/b&gt; two' and 'beta &lt;b&gt;two&lt;/b&gt;', as with the default gap of 1.
- Actual: You get 'be&lt;b&gt;ta t&lt;/b&gt;wo' and the unhighlighted 'alpha one', because indexing put the second value 3 characters after the first while the highlighter rebuilds the text with a single separator character between values, so every offset in the second value points 2 characters too far.

## Files

- [src/org/apache/lucene/search/uhighlight/TestUnifiedHighlighterOffsetGap.java](src/org/apache/lucene/search/uhighlight/TestUnifiedHighlighterOffsetGap.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.uhighlight.TestUnifiedHighlighterOffsetGap
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
