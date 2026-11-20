# ScorerUtil.minRequiredScore (new in 10.3.0, #14827) can loop forever when the remaining clauses' max score is negative at a minimum competitive score of 0, so with assertions disabled a top-k BooleanQuery with a negative-scoring SHOULD clause can spin forever in MaxScoreBulkScorer, the infinite loop that LUCENE-10428 had turned into an exception

Finding LFV-1016 · Area: search / top-k dynamic pruning (ScorerUtil.minRequiredScore, called by MaxScoreBulkScorer) · Priority: medium · Impact: hang · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run IndexSearcher.search(q, 10) on a 3-document index, where q ORs a clause that scores every document -1 and reports -1 as its max score with a MatchAllDocsQuery, and expect it to return the 3 documents or throw, as 10.2.2 does and as Lucene has promised since LUCENE-10428 (9.1.0: 'Queries with a misbehaving score function may no longer cause infinite loops in their parent BooleanQuery'). With assertions disabled it never returns: MaxScoreBulkScorer treats the -1 clause as non-essential at the initial minimum competitive score 0 and calls ScorerUtil.minRequiredScore(-1.0, 0f, 2), whose loop starts at 1.0 and subtracts Math.ulp(0f) = 1.4e-45, a step that is lost in double arithmetic, so its exit test never changes. The same happens without custom code through RescoreTopNQuery over negative values, and IndexSearcher#setTimeout does not stop it.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: While the reported behaviour is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/search/TestMinRequiredScoreTermination.java](src/org/apache/lucene/search/TestMinRequiredScoreTermination.java) (JUnit test)

## Run it

- Tested on Lucene main 1cfa86310f76 (a commit of 2026-11-17): still fails 5 of 5 without assertions and 2 of 5 with assertions, the two direct calls of ScorerUtil.minRequiredScore (run on 2026-11-18 with the test adapted as the 781025d6fe32 line says); the re-run of 2026-11-17 on this commit ran another test of this finding, a one-test class that is not in this folder (the BooleanQuery search on a 3-document index, with MatchAllDocsQuery.INSTANCE): it passed with assertions on and failed with them off, the search not returning within 10 s.
- Tested on Lucene main 9965d34e3c77 (a commit of 2026-11-16): still fails 5 of 5 without assertions and 2 of 5 with assertions, the two direct calls of ScorerUtil.minRequiredScore (run on 2026-11-17 with the test adapted as the next line says); the re-run of 2026-11-16 on this commit ran another test of this finding, a one-test class that is not in this folder (the BooleanQuery search on a 3-document index, with MatchAllDocsQuery.INSTANCE): it passed with assertions on and failed with them off, the search not returning within 10 s.
- Tested on Lucene main 781025d6fe32 (a commit of 2026-11-13): still fails 5 of 5 without assertions and 2 of 5 with assertions, the two direct calls of ScorerUtil.minRequiredScore (run with the test adapted to #16962, which made the no-argument MatchAllDocsQuery constructor private on main (24d9e7d1a0d) and on the 11.0 branch (branch_11x, a6a26f9b251): lines 130 and 157 pass MatchAllDocsQuery.INSTANCE instead of new MatchAllDocsQuery()).
- Tested on Lucene main 645d4fe14a20 (a commit of 2026-10-28): reproduced, with the test as shipped.
- The runs of the five tests on 1cfa86310f76, 9965d34e3c77 and 781025d6fe32 took one test per JVM, with --select-method org.apache.lucene.search.TestMinRequiredScoreTermination#testBooleanQueryWithMisbehavingClauseTerminates (and so on for the other four tests) in place of --select-class: a call that does not return leaves its thread running until the JVM exits. The command block runs the five tests in one JVM, as the run on 645d4fe14a20 did.
- This finding was re-run on Lucene main f2b061406c4b on 2026-11-19: still reproduces (the repro adapted to an API change on this commit).
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
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

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

Formal models and scripts this finding's part of the hunt used: [../../models/rescoretopn-negative-rescore-values-maxscore-hang/](../../models/rescoretopn-negative-rescore-values-maxscore-hang/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
