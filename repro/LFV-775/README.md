# UnifiedHighlighter cuts a stored value at maxLength UTF-16 units even between the two halves of a supplementary character (an emoji, a CJK Extension B ideograph), so the snippet can end in an unpaired high surrogate, for example '&lt;b&gt;Hello&lt;/b&gt; \uD83D'

Bug LFV-775 · Area: highlighter / UnifiedHighlighter stored-value loading (LimitedStoredFieldVisitor maxLength cut) · Priority: low · Impact: minor · Novelty: new

Bug page: [../../bugs/LFV-775.html](../../bugs/LFV-775.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You store the value 'Hello U+1F600 world' (the emoji takes UTF-16 indexes 6 and 7), search for hello and highlight the hit with UnifiedHighlighter.builder(searcher, analyzer).withMaxLength(7), and expect a snippet made of whole characters, '&lt;b&gt;Hello&lt;/b&gt; ', just as maxLength 6 gives '&lt;b&gt;Hello&lt;/b&gt; ' and maxLength 8 gives the whole emoji. Instead LimitedStoredFieldVisitor keeps exactly 7 UTF-16 units, so the content and the snippet end with the first half of the emoji, '&lt;b&gt;Hello&lt;/b&gt; \uD83D', which String.getBytes(UTF_8) turns into '&lt;b&gt;Hello&lt;/b&gt; ?' and a strict UTF-8 encoder rejects. The same happens at the default maxLength 10,000 when a value's 10,000th unit is the first half of a supplementary character, and for a later value of a multi-valued field at its per-value budget.

The test shows that with offsets in postings, `UnifiedHighlighter.withMaxLength(7)` cuts the stored value "Hello U+1F600 world" between the two UTF-16 halves of the emoji, so the snippet ends in an unpaired high surrogate.

## Expected vs actual

Expected a well-formed snippet (whole characters only; maxLength 6 gives `<b>Hello</b> `). Actual: `<b>Hello</b> \uD83D`.

## Files

- [src/org/apache/lucene/search/uhighlight/TestUnifiedHighlighterMaxLengthSurrogatePair.java](src/org/apache/lucene/search/uhighlight/TestUnifiedHighlighterMaxLengthSurrogatePair.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.uhighlight.TestUnifiedHighlighterMaxLengthSurrogatePair
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
