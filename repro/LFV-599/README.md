# UnifiedHighlighter mistakes a U+0000 inside a stored value for its internal multi-value separator, so snippets are cut there and, with the ANALYSIS offset source, matches the query found are not highlighted

Bug LFV-599 · Area: highlighter / UnifiedHighlighter (multi-value joining with the in-band MULTIVAL_SEP_CHAR U+0000: SplittingBreakIterator, ANALYSIS re-analysis) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You store and index one text value that contains a U+0000 character, for example 'ab\u0000cd' in a KeywordAnalyzer field, find it with a TermQuery for that exact value and highlight the hit with the default UnifiedHighlighter; you expect the same snippet as for the twin value with U+0001, which has identical postings and is highlighted as '&lt;b&gt;ab\u0001cd&lt;/b&gt;'. Instead the highlighter, which joins a field's values with U+0000 internally and then searches for U+0000 to find where values end, takes the character for a boundary between two values: the snippet ends there, and the ANALYSIS offset source re-analyzes 'ab' and 'cd' separately, finds no match and returns the plain summary 'ab'.

## Expected vs actual

- Expected: You expect the same snippet as for the twin value with U+0001, which has identical postings and is highlighted as '&lt;b&gt;ab\u0001cd&lt;/b&gt;'.
- Actual: The highlighter, which joins a field's values with U+0000 internally and then searches for U+0000 to find where values end, takes the character for a boundary between two values: the snippet ends there, and the ANALYSIS offset source re-analyzes 'ab' and 'cd' separately, finds no match and returns the plain summary 'ab'.

## Files

- [src/org/apache/lucene/search/uhighlight/TestUHEmbeddedNulInValue.java](src/org/apache/lucene/search/uhighlight/TestUHEmbeddedNulInValue.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/highlighter/build/libs/lucene-highlighter-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.uhighlight.TestUHEmbeddedNulInValue
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
