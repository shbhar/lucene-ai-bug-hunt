# MaxScoreBulkScorer can skip a document whose score equals the minimum competitive score (1-ulp rank inversion)

Bug LFV-060 · Area: search / scoring · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-060.html](../../bugs/LFV-060.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run a top-k disjunction of 4 or more SHOULD clauses whose clause scores span more than 2^29. You expect every document that is competitive by its own score to be collected. Instead the required-clause test skips MathUtil.sumUpperBound, so a document whose score equals minCompetitiveScore can be dropped and the top hits change by one rank.

The test shows that `MaxScoreBulkScorer` skips a document whose score equals `minCompetitiveScore`: for 4 SHOULD clauses its required-clause test rounds the bound without `MathUtil.sumUpperBound` to 4f, below doc 0's score nextUp(4f), so a clause doc 0 does not match becomes required.

## Expected vs actual

With `setMinCompetitiveScore(nextUp(4f))`, expected doc 0 to be collected (`[4.0000005]`, as with min score 4f); actual `[]`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/search/TestMaxScoreRequiredClauseRounding.java](src/org/apache/lucene/search/TestMaxScoreRequiredClauseRounding.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestMaxScoreRequiredClauseRounding
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/study-topk-pruning-proof/](../../models/study-topk-pruning-proof/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
