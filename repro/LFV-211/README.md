# FunctionScoreQuery's block-max bound multiplies by the wrapped query's no-score max score (0 for a TermQuery), so a BooleanQuery that contains it can drop the true top hits

Finding LFV-211 · Area: queries / FunctionScoreQuery block-max pruning (#16431) · Priority: medium · Impact: silent wrong results · Novelty: new

Finding page: [../../bugs/LFV-211.html](../../bugs/LFV-211.html)

AI-assisted finding, confirmed by automated checks. Upstream status: Fix in progress, by harshvardhanrp (issue #17050 and open PR #17051) on 2026-11-19 (https://github.com/apache/lucene/pull/17051). Issue #17050 (harshvardhanrp, opened 2026-11-19: 'FunctionScoreQuery block-max bound drops top hits when the value field has a doc values skip index') is open; the open PR #17051 (same author, opened 2026-11-19; its description says 'Fixes #17050'; base main; no milestone and no review as of 2026-11-20) changes FunctionScoreQuery.java, DoubleValues.java and DoubleValuesSource.java. The issue describes this defect: the block-max bound multiplies by the wrapped query's max score, which is 0 for a TermQuery created without scores. On a build of the pull request as of 2026-11-19 our repro passes, with and without assertions; it fails on main f2b061406c4b and on branch_11x 75eea467ac52. Neither item cites this list. They are not an earlier report of it: this row has been in the site's repository since its first commit of 2026-10-27, and both were opened on 2026-11-19. This site files nothing upstream.

## What it shows

You index a popularity field with a skip index (NumericDocValuesField.indexedField("popularity", v)) and search BooleanQuery(SHOULD FunctionScoreQuery(TermQuery(body:a), DoubleValuesSource.fromLongField("popularity")), SHOULD TermQuery(body:b)) for the top hit, expecting the same hit as an exhaustive search, because the function query scores each matching document by its popularity value. Instead, with 4,097 documents that all match body:a, where only doc 4096 has popularity 100 (all others 1) and only doc 0 matches body:b, TOP_SCORES and the plain IndexSearcher.search(q, 1) return doc 0 (score 4.597) instead of doc 4096 (score 100). The cause is that the scorer's getMaxScore returns innerMaxScore * valueMaxScore * boost, where innerMaxScore is the max score of the wrapped TermQuery, which FunctionScoreQuery itself weighted without scores (so it reports 0), although score() is value * boost and never reads the wrapped score; every block therefore reports a bound of 0, and MaxScoreBulkScorer stops scoring documents that only the function clause makes competitive.

The test shows that `BooleanQuery(SHOULD FunctionScoreQuery(TermQuery(body:a), fromLongField("popularity")), SHOULD TermQuery(body:b))` over a skip-indexed popularity field (`NumericDocValuesField.indexedField`) returns the wrong top hit from the default `IndexSearcher.search(query, 1)`. The cause is that the function clause's block-max bound is the wrapped no-score TermQuery's max score of 0 times the value bound.

## Expected vs actual

Expected: doc 4096 (popularity 100, score 100). Actual on main with -ea and with -da: doc 0 (score 4.597 = 1 + BM25 of body:b), `expected:<4096> but was:<0>`.

## Files

- [src/org/apache/lucene/queries/function/TestFunctionScoreQueryBooleanTopHit.java](src/org/apache/lucene/queries/function/TestFunctionScoreQueryBooleanTopHit.java) (JUnit test)

## Run it

- Tested on Lucene main b5a4546a5964 (a commit of 2026-11-22): still reproduces.
- Tested on Lucene main 645d4fe14a20 (a commit of 2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.function.TestFunctionScoreQueryBooleanTopHit
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

Formal models and scripts this finding's part of the hunt used: [../../models/function-queries-bmw/](../../models/function-queries-bmw/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
