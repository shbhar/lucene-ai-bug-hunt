# UnifiedHighlighter.highlightWithoutSearcher ignores maxLength (the default 10,000 included): it analyzes and highlights the whole supplied text, although getMaxLength and Builder.withMaxLength document that content is truncated to maxLength before highlighting

Bug LFV-756 · Area: highlighter / UnifiedHighlighter (highlightWithoutSearcher entry point, maxLength option) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a UnifiedHighlighter without a searcher (builderWithoutSearcher, optionally withMaxLength(20)) and call highlightWithoutSearcher on a text longer than maxLength, expecting the text to be cut to its first maxLength chars before highlighting, as getMaxLength ('Content will be truncated to this size before highlighting') and withMaxLength document and as the searcher entry points do. Instead the whole text is analyzed and highlighted: with maxLength 20, the 48-char text 'Alpha beta gamma. Delta epsilon. Lucene is here.' gives '&lt;b&gt;Lucene&lt;/b&gt; is here.' (its only match is at offset 33) where the searcher path returns 'Alpha beta gamma. ', and with the default maxLength of 10,000 a match at char 10,541 of a 10,560-char text is highlighted. The only truncation lives in the stored-field loader (LimitedStoredFieldVisitor), which this entry point never calls, although the re-analysis strategy it uses relies on it ('content is already truncated to maxLength').

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The whole text is analyzed and highlighted: with maxLength 20, the 48-char text 'Alpha beta gamma.

## Files

- [src/org/apache/lucene/search/uhighlight/TestHighlightWithoutSearcherMaxLength.java](src/org/apache/lucene/search/uhighlight/TestHighlightWithoutSearcherMaxLength.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.uhighlight.TestHighlightWithoutSearcherMaxLength
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
