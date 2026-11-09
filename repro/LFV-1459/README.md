# matchhighlight's default BreakIteratorShrinkingAdjuster moves the end of a marker-less default passage back past the passage start when no word ends inside the passage and returns an empty range before it, so PassageSelector.pickBest fails its own 'Adjusters must not expand the passage's range' assertion with -ea and silently drops that value's default passage with -da (values '01234' and '56789' at window 4 give one passage, not two); since 9.0.0

Finding LFV-1459 · Area: highlighter / matchhighlight passage adjustment (BreakIteratorShrinkingAdjuster, PassageSelector.pickBest default passages, FieldValueHighlighters.defaultPassageSelector) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1459.html](../../bugs/LFV-1459.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You ask matchhighlight's default passage selector for up to two passages of a two-valued field without matches, FieldValueHighlighters.defaultPassageSelector().pickBest("01234 56789", List.of(), 4, 2, [0,5), [6,11)) (the values '01234' and '56789' joined with a one-character gap, as MatchHighlighter joins them), and expect one truncated default passage per value, [0,4) and [6,10), just as the first value keeps its truncated passage. Instead BreakIteratorShrinkingAdjuster walks the end of the second passage [6,10) back to the last word end before it, offset 5, and returns the empty range [5,5), which lies before the passage: with -ea pickBest throws 'AssertionError: Adjusters must not expand the passage's range: was [[from=6, to=10], markers=[]] =&gt; changed to [from=5, to=5]', and with -da it returns only [0,4), so the second value gets no snippet.

## Expected vs actual

- Expected: 2 passages, [0,4) and [6,10) (`0123...` and `5678...`).
- Actual: Assertions on: `Adjusters must not expand the passage's range: was [[from=6, to=10], markers=[]] => changed to [from=5, to=5]`; off: only [0,4), `expected:<2> but was:<1>`.

## Files

- [src/org/apache/lucene/search/matchhighlight/TestDefaultPassagePerValueWithAdjuster.java](src/org/apache/lucene/search/matchhighlight/TestDefaultPassagePerValueWithAdjuster.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
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

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.matchhighlight.TestDefaultPassagePerValueWithAdjuster
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestDefaultPassagePerValueWithAdjuster.java](TestDefaultPassagePerValueWithAdjuster.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
