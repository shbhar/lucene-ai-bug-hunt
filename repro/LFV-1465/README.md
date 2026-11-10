# A repeated multi-term interval source such as Intervals.ordered(prefix(caf), prefix(caf)) returns non-null Weight#matches, and the default UnifiedHighlighter marks the word, for a document it does not match when the field indexes two matching terms at one position (the one-word text 'café' with ASCIIFoldingFilter preserveOriginal), because MultiTermIntervalsSource.matches reports one entry per expanded term at a position while intervals() reports the position once; since 8.5.0

Finding LFV-1465 · Area: queries / intervals: Matches of multi-term interval sources (MultiTermIntervalsSource.matches) under repeated sources (RepeatingIntervalsSource), consumed by Weight#matches and the UnifiedHighlighter · Priority: low · Impact: silent wrong results · Novelty: new

Finding page: [../../bugs/LFV-1465.html](../../bugs/LFV-1465.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index the one-word text 'café' with WhitespaceTokenizer + ASCIIFoldingFilter(preserveOriginal=true), which puts 'café' and 'cafe' at position 0, and run IntervalQuery("f", Intervals.ordered(prefix("caf"), prefix("caf"))), that is two words starting with 'caf'. Search agrees that the document does not match (count 0), so Weight#matches must return null, as its javadoc says and as the same query written with Intervals.or(term("cafe"), term("café")) does. Instead Weight#matches returns a match at [0,0]: MultiTermIntervalsSource.matches merges the two expanded terms with MatchesUtils.disjunction, which lists position 0 once per term while intervals() lists it once, and the repeated source counts the two entries as two occurrences, so the default UnifiedHighlighter marks 'café' in 'café bar' for a hit of 'bar OR ordered(caf*, caf*)' that matched only through 'bar'.

## Expected vs actual

- Expected: `weight.matches(leaf, 0)` is `null`: the query does not match doc 0 (`count` is 0).
- Actual: A non-null `Matches`: `AssertionError: ... expected null, but was:<...MatchesUtils$3@...>` at line 54, with assertions on and off.

## Files

- [src/org/apache/lucene/queries/intervals/TestMultiTermIntervalsStackedMatches.java](src/org/apache/lucene/queries/intervals/TestMultiTermIntervalsStackedMatches.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.intervals.TestMultiTermIntervalsStackedMatches
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestMultiTermIntervalsStackedMatches.java](TestMultiTermIntervalsStackedMatches.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
