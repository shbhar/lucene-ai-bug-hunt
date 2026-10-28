# UnifiedHighlighter.highlightWithoutSearcher returns the text with no highlights under its default flags (WEIGHT_MATCHES) when the query also has a required or filter clause on another field

Bug LFV-913 · Area: highlighter / UnifiedHighlighter re-analysis without a searcher (highlightWithoutSearcher: MemoryIndexOffsetStrategy reader == null branch + WEIGHT_MATCHES) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a UnifiedHighlighter without a searcher (builderWithoutSearcher, default flags) and call highlightWithoutSearcher("body", query, "a b c", 1) with +title:x +body:a or +body:a #year:[2000 TO 2030], expecting '&lt;b&gt;a&lt;/b&gt; b c', which highlight gives for the same text on an indexed one-document copy, which the same call gives with withWeightMatches(false), and which 8.x gave by default. Instead it returns 'a b c' with no mark: under WEIGHT_MATCHES the whole query's Weight#matches runs on a MemoryIndex that holds only the highlighted field, where the other-field clause cannot match, so Matches is null ('doc doesn't match') and the highlighter falls back to the unmarked summary. The searcher path answers other-field clauses from the real index (OverlaySingleDocTermsLeafReader); the no-searcher branch has no such view, so a clause it cannot evaluate is treated as false.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: It returns 'a b c' with no mark: under WEIGHT_MATCHES the whole query's Weight#matches runs on a MemoryIndex that holds only the highlighted field, where the other-field clause cannot match, so Matches is null ('doc doesn't match') and the highlighter falls back to the unmarked summary.

## Files

- [src/org/apache/lucene/search/uhighlight/TestHighlightWithoutSearcherOtherFieldClauses.java](src/org/apache/lucene/search/uhighlight/TestHighlightWithoutSearcherOtherFieldClauses.java) (JUnit test)

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

# run 1: assertions off (production-like), seed C0FFEE368
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=C0FFEE368 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.uhighlight.TestHighlightWithoutSearcherOtherFieldClauses
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
