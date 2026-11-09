# Intervals.atLeast Matches report offsets and sub-matches from a source the minimisation loop already dropped, so highlighting marks tokens outside the match

Finding LFV-1453 · Area: queries / intervals (Intervals.atLeast Matches path: MinimumShouldMatchIntervalsSource) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index 'the quick' with 'fast' stacked on 'quick' as an index-time synonym (position increment 0, offsets [4,9)) and ask Weight#matches for Intervals.atLeast(2, the, quick, fast); you expect its only minimal interval, positions [1,1] (quick and fast), with offsets [4,9), and for the text 'the quick fox' the default UnifiedHighlighter snippet 'the &lt;b&gt;quick&lt;/b&gt; fox'. Instead the match keeps positions [1,1] but reports startOffset 0 (offsets [0,9)) and a sub-match for 'the' at position 2147483647 (NO_MORE_INTERVALS), so the highlighter returns '&lt;b&gt;the&lt;/b&gt; &lt;b&gt;quick&lt;/b&gt; fox'. The minimisation loop in nextInterval() popped 'the' as its lead in an earlier round and then returned through its in-loop exit without clearing it, and getCurrentIterators() still adds that lead to the offsets and sub-matches.

## Expected vs actual

- Expected: You expect its only minimal interval, positions [1,1] (quick and fast), with offsets [4,9), and for the text 'the quick fox' the default UnifiedHighlighter snippet 'the &lt;b&gt;quick&lt;/b&gt; fox'.
- Actual: The match keeps positions [1,1] but reports startOffset 0 (offsets [0,9)) and a sub-match for 'the' at position 2147483647 (NO_MORE_INTERVALS), so the highlighter returns '&lt;b&gt;the&lt;/b&gt; &lt;b&gt;quick&lt;/b&gt; fox'.

## Files

- [src/org/apache/lucene/queries/intervals/TestAtLeastMatchOffsets.java](src/org/apache/lucene/queries/intervals/TestAtLeastMatchOffsets.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.intervals.TestAtLeastMatchOffsets
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
