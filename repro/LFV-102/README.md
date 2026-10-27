# CheckHits.checkTopScores (test framework) fails on correct minimum-should-match disjunctions because it walks the COMPLETE and TOP_SCORES two-phase approximations in lockstep

Bug LFV-102 · Area: test-framework / search checkers (CheckHits) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

In a test you call the test framework's CheckHits.checkTopScores on a BooleanQuery of three term clauses with setMinimumNumberShouldMatch(2), for example (f:a f:b f:c)~2. You expect it to pass, because the query returns the same matching docs with the same scores in COMPLETE and TOP_SCORES mode (checked in the same test). Instead it throws AssertionError 'expected:&lt;1&gt; but was:&lt;0&gt;' in CheckHits.doCheckMaxScores (CheckHits.java:751): the checker requires the two scorers' two-phase approximations, which only have to be supersets of the matches, to stop on the same docs, and the TOP_SCORES WANDScorer legitimately stops on doc 0 (it holds only 'c'), which matches then rejects.

## Expected vs actual

- Expected: You expect it to pass, because the query returns the same matching docs with the same scores in COMPLETE and TOP_SCORES mode (checked in the same test).
- Actual: It throws AssertionError 'expected:&lt;1&gt; but was:&lt;0&gt;' in CheckHits.doCheckMaxScores (CheckHits.java:751): the checker requires the two scorers' two-phase approximations, which only have to be supersets of the matches, to stop on the same docs, and the TOP_SCORES WANDScorer legitimately stops on doc 0 (it holds only 'c'), which matches then rejects.

## Files

- [src/org/apache/lucene/search/TestCheckTopScoresMinShouldMatch.java](src/org/apache/lucene/search/TestCheckTopScoresMinShouldMatch.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestCheckTopScoresMinShouldMatch
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
