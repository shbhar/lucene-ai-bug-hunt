# MultiTermIntervalsSource.matches checks the expansion limit with a post-increment ('count++ &gt; maxExpansions'), so Weight#matches, and highlighting through it, accepts a document with exactly maxExpansions + 1 matching terms, where the javadoc and the search path (which uses '++count') reject it (IllegalStateException; IndexSearcher.TooManyClauses on main since #16899)

Bug LFV-462 · Area: queries / intervals (MultiTermIntervalsSource behind Intervals.prefix, wildcard, regexp, range, fuzzyTerm and multiterm; Weight#matches / IntervalsSource#matches) · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build IntervalQuery("f", Intervals.prefix(new BytesRef("p"), 2)) over a document 'pa pb pc' (three terms match p*) and call Weight#matches for it, as a highlighter does. You expect the over-expansion exception (IllegalStateException; IndexSearcher.TooManyClauses on main since #16899), because the javadoc says the source throws if the prefix 'expands to more than maxExpansions terms', and searching the same query on the same segment does throw. Instead matches tests 'count++ &gt; maxExpansions' after adding each term, so it returns a Matches for exactly maxExpansions + 1 matching terms in the document and throws only at maxExpansions + 2; the same holds for wildcard, regexp, range, fuzzyTerm and multiterm.

## Expected vs actual

- Expected: You expect the over-expansion exception (IllegalStateException; IndexSearcher.TooManyClauses on main since #16899), because the javadoc says the source throws if the prefix 'expands to more than maxExpansions terms', and searching the same query on the same segment does throw.
- Actual: matches tests 'count++ &gt; maxExpansions' after adding each term, so it returns a Matches for exactly maxExpansions + 1 matching terms in the document and throws only at maxExpansions + 2; the same holds for wildcard, regexp, range, fuzzyTerm and multiterm.

## Files

- [src/org/apache/lucene/queries/intervals/TestMultiTermMatchesExpansionLimit.java](src/org/apache/lucene/queries/intervals/TestMultiTermMatchesExpansionLimit.java) (JUnit test)
- [src2/org/apache/lucene/queries/intervals/TestMultiTermMatchesExpansionLimit.java](src2/org/apache/lucene/queries/intervals/TestMultiTermMatchesExpansionLimit.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces with the copy in src/, adapted for #16899 (two lines: the expected exception type).
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced with the original copy in src2/.
- Two copies of the test: src/ expects IndexSearcher.TooManyClauses, the over-expansion exception on main since #16899 (f4cc6ac2097c), so run it on a9779ef3c193 or later; src2/ is the recorded original, which expects IllegalStateException and is for main before #16899 (for example ea57360912ed). Both fail at the same matches() check while the bug is present.
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

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.intervals.TestMultiTermMatchesExpansionLimit
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.intervals.TestMultiTermMatchesExpansionLimit
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
