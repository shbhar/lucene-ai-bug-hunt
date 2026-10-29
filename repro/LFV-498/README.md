# UnifiedHighlighter's no-match summary of a multi-valued field counts the internal U+0000 value separator as a passage, so the summary contains a NUL character and shows one value or sentence fewer than requested

Bug LFV-498 · Area: highlighter / UnifiedHighlighter default summary (FieldHighlighter.getSummaryPassagesNoHighlight over the SplittingBreakIterator that splits multi-valued fields on MULTIVAL_SEP_CHAR) · Priority: low · Impact: minor · Novelty: new

Bug page: [../../bugs/LFV-498.html](../../bugs/LFV-498.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You store two values in one field ('First value.' and 'Second value.'), run a query that matches the document but not that field, and ask the UnifiedHighlighter for 2 passages; you expect the documented default summary, the first 2 sentences from the field (for example 'First value.... Second value.', the way the highlight path joins values). Instead you get 'First value.\u0000': the highlighter joins the values with an internal U+0000 separator, its SplittingBreakIterator reports a boundary on both sides of that character, and FieldHighlighter.getSummaryPassagesNoHighlight turns the 1-character gap into a passage, so the NUL is returned and takes the second value's slot (3 passages give 'First value.\u0000Second value.'). With an empty first value even 1 passage returns just '\u0000'.

## Expected vs actual

- Expected: `First value.... Second value.` for 2 and for 3 passages, and `Second value.` when the first value is empty.
- Actual: `First value.\u0000`, then `First value.\u0000Second value.`, then `\u0000` alone; both controls pass.

## Files

- [src/org/apache/lucene/search/uhighlight/TestUHSummaryMultiValueSeparator.java](src/org/apache/lucene/search/uhighlight/TestUHSummaryMultiValueSeparator.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.uhighlight.TestUHSummaryMultiValueSeparator
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestUnifiedHighlighterMultiValuedSummary.java](TestUnifiedHighlighterMultiValuedSummary.java)
- [fix-B-skip-separator-on-copy.diff](fix-B-skip-separator-on-copy.diff)
- [TestUHSummaryMultiValueSeparator.java](TestUHSummaryMultiValueSeparator.java)
- [TestMultiValuedNoMatchSummary.java](TestMultiValuedNoMatchSummary.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
