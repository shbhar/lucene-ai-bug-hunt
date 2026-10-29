# ScorerUtil.minRequiredScore (new in 10.3.0, #14827) never terminates when the remaining clauses' max score is negative at a minimum competitive score of 0, so with assertions disabled a top-k BooleanQuery with a negative-scoring SHOULD clause spins forever in MaxScoreBulkScorer, the infinite loop that LUCENE-10428 had turned into an exception

Bug LFV-1016 · Area: search / top-k dynamic pruning (ScorerUtil.minRequiredScore, called by MaxScoreBulkScorer) · Priority: unranked · Impact: hang · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run IndexSearcher.search(q, 10) on a 3-document index, where q ORs a clause that scores every document -1 and reports -1 as its max score with a MatchAllDocsQuery, and expect it to return the 3 documents or throw, as 10.2.2 does and as Lucene has promised since LUCENE-10428 (9.1.0: 'Queries with a misbehaving score function may no longer cause infinite loops in their parent BooleanQuery'). With assertions disabled it never returns: MaxScoreBulkScorer treats the -1 clause as non-essential at the initial minimum competitive score 0 and calls ScorerUtil.minRequiredScore(-1.0, 0f, 2), whose loop starts at 1.0 and subtracts Math.ulp(0f) = 1.4e-45, a step that is lost in double arithmetic, so its exit test never changes. The same happens without custom code through RescoreTopNQuery over negative values, and IndexSearcher#setTimeout does not stop it.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/search/TestMinRequiredScoreTermination.java](src/org/apache/lucene/search/TestMinRequiredScoreTermination.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=4 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestMinRequiredScoreTermination
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/rescoretopn-negative-rescore-values-maxscore-hang/](../../models/rescoretopn-negative-rescore-values-maxscore-hang/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
