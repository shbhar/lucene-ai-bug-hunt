# UnifiedHighlighter with a LengthGoalBreakIterator length goal of 0 throws IllegalArgumentException 'offset out of bounds' instead of returning the default summary for every hit that has no match in the field (8.5.0 regression from LUCENE-9093)

Bug LFV-493 · Area: highlighter / UnifiedHighlighter break iterators (LengthGoalBreakIterator.next on the no-match default-summary path, FieldHighlighter.getSummaryPassagesNoHighlight) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You give the UnifiedHighlighter LengthGoalBreakIterator.createMinLength(base, 0, a) or createClosestToLength(base, 0, a): both factories accept a goal of 0, and with it matches are highlighted exactly as with the base iterator. When a hit has no match in the field, you expect the documented default summary, the first maxPassages sentences (for example 'First sentence. '). Instead next computes the target 0 + 0 = 0 and calls the base iterator's following(-1), which every java.text.BreakIterator rejects, so IllegalArgumentException 'offset out of bounds' escapes and the whole highlight call fails; goal 0 worked on this path from 6.4.0 through 8.4.1.

## Expected vs actual

- Expected: You expect the documented default summary, the first maxPassages sentences (for example 'First sentence. ').
- Actual: next computes the target 0 + 0 = 0 and calls the base iterator's following(-1), which every java.text.BreakIterator rejects, so IllegalArgumentException 'offset out of bounds' escapes and the whole highlight call fails; goal 0 worked on this path from 6.4.0 through 8.4.1.

## Files

- [src/org/apache/lucene/search/uhighlight/TestLengthGoalZeroGoalSummary.java](src/org/apache/lucene/search/uhighlight/TestLengthGoalZeroGoalSummary.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.uhighlight.TestLengthGoalZeroGoalSummary
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
