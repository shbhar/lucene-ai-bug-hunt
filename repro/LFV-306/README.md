# RepeatingIntervalsSource's iterator computes width with the sign inverted (start - end + 1 per repeated sub-interval), so when Intervals.ordered/unordered repeat a sub-source wider than one position (for example the same phrase twice) gaps is over-reported, maxgaps drops real matches and Weight#matches disagrees with the scorer

Bug LFV-306 · Area: queries / intervals (Intervals.ordered/unordered with equal sub-sources: RepeatingIntervalsSource, maxgaps) · Priority: medium · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You search with an interval query that repeats a multi-word piece, for example maxgaps(0, ordered(phrase(new, york), phrase(new, york))) on the text 'new york new york', and expect a match because the two phrases sit back to back with no gap. Instead it finds nothing: Lucene turns equal sub-sources into a RepeatingIntervalsSource whose iterator adds start - end + 1 instead of end - start + 1 for each copy, so width is 0 instead of 4, gaps is 4 instead of 0, and maxgaps rejects the interval. The Matches path of the same class uses the right formula, so Weight#matches returns a match for the very document the scorer rejected; repeats of single words are unaffected because their start equals their end.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: It finds nothing: Lucene turns equal sub-sources into a RepeatingIntervalsSource whose iterator adds start - end + 1 instead of end - start + 1 for each copy, so width is 0 instead of 4, gaps is 4 instead of 0, and maxgaps rejects the interval.

## Files

- [src/org/apache/lucene/queries/intervals/TestRepeatedSourceWidth.java](src/org/apache/lucene/queries/intervals/TestRepeatedSourceWidth.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.intervals.TestRepeatedSourceWidth
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
