# UnifiedHighlighter with WEIGHT_MATCHES (the default) and a field matcher that accepts other fields (requireFieldMatch=false) highlights nothing in a hit whose query has a required clause on another field, because the matcher's remap evaluates that clause against the highlighted field's terms

Bug LFV-515 · Area: highlighter / UnifiedHighlighter WEIGHT_MATCHES path (FieldOffsetStrategy field-matcher remap) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a document with text 'red shirt sale', title 'summer collection' and the keyword status 'active', search +title:summer +text:shirt (or +text:shirt #status:active), and highlight 'text' with UnifiedHighlighter.builder(searcher, analyzer).withFieldMatcher(f -&gt; true).build. You expect 'red &lt;b&gt;shirt&lt;/b&gt; sale', which the default field matcher and the same matcher with withWeightMatches(false) both return. Instead you get 'red shirt sale' with no highlight, because the WEIGHT_MATCHES path makes every accepted field read the highlighted field's terms, so the required clause on title (or status) finds no 'summer' ('active') in text, Weight#matches of the whole query is null, and the highlighter treats the hit as not matching.

## Expected vs actual

- Expected: You expect 'red &lt;b&gt;shirt&lt;/b&gt; sale', which the default field matcher and the same matcher with withWeightMatches(false) both return.
- Actual: You get 'red shirt sale' with no highlight, because the WEIGHT_MATCHES path makes every accepted field read the highlighted field's terms, so the required clause on title (or status) finds no 'summer' ('active') in text, Weight#matches of the whole query is null, and the highlighter treats the hit as not matching.

## Files

- [src/org/apache/lucene/search/uhighlight/TestUnifiedHighlighterFieldMatcherRequiredClause.java](src/org/apache/lucene/search/uhighlight/TestUnifiedHighlighterFieldMatcherRequiredClause.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.uhighlight.TestUnifiedHighlighterFieldMatcherRequiredClause
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
