# MultiTermIntervalsSource.matches checks the expansion limit with a post-increment ('count++ &gt; maxExpansions'), so Weight#matches, and highlighting through it, accepts a document with exactly maxExpansions + 1 matching terms, where the javadoc and the search path (which uses '++count') reject it (IllegalStateException; IndexSearcher.TooManyClauses on main since #16899)

Bug LFV-462 · Area: queries / intervals (MultiTermIntervalsSource behind Intervals.prefix, wildcard, regexp, range, fuzzyTerm and multiterm; Weight#matches / IntervalsSource#matches) · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build IntervalQuery("f", Intervals.prefix(new BytesRef("p"), 2)) over a document 'pa pb pc' (three terms match p*) and call Weight#matches for it, as a highlighter does. You expect the over-expansion exception (IllegalStateException; IndexSearcher.TooManyClauses on main since #16899), because the javadoc says the source throws if the prefix 'expands to more than maxExpansions terms', and searching the same query on the same segment does throw. Instead matches tests 'count++ &gt; maxExpansions' after adding each term, so it returns a Matches for exactly maxExpansions + 1 matching terms in the document and throws only at maxExpansions + 2; the same holds for wildcard, regexp, range, fuzzyTerm and multiterm.

The test shows that `Weight#matches` for `IntervalQuery("f", Intervals.prefix(new BytesRef("p"), 2))` on one document `pa pb pc` (3 = maxExpansions + 1 matching terms) returns a `Matches`, while searching the same query throws (`MultiTermIntervalsSource.matches()` tests `count++ > maxExpansions`, the search path `++count`).

## Expected vs actual

Expected `IndexSearcher.TooManyClauses` (javadoc: thrown if the prefix expands to more than maxExpansions terms); actual: no exception (`Expected exception TooManyClauses but no exception was thrown`).

## Files

- [src/org/apache/lucene/queries/intervals/TestMultiTermIntervalsMatchesLimit.java](src/org/apache/lucene/queries/intervals/TestMultiTermIntervalsMatchesLimit.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.intervals.TestMultiTermIntervalsMatchesLimit
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
