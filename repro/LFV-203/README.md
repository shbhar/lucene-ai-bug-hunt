# FunctionScoreQuery over a skip-indexed doc-values field scores negative values as 0, but its block-max bound can be negative, so the top-k search of a BooleanQuery that has it as a scoring clause can skip competitive documents or, next to another scoring clause, not return (main and branch_11x, both unreleased)

Finding LFV-203 · Area: queries / FunctionScoreQuery block-max pruning (#16431) · Priority: medium · Impact: silent wrong results · Novelty: new

Finding page: [https://shbhar.github.io/lucene-ai-bug-hunt/bugs/LFV-203.html](https://shbhar.github.io/lucene-ai-bug-hunt/bugs/LFV-203.html)

AI-assisted finding, confirmed by automated checks. Upstream status: Fix in progress, by harshvardhanrp (open PR #17051) on 2026-11-19 (https://github.com/apache/lucene/pull/17051). Issue #17050 (harshvardhanrp, opened 2026-11-19: 'FunctionScoreQuery block-max bound drops top hits when the value field has a doc values skip index') is open; the open PR #17051 (same author, opened 2026-11-19; its description says 'Fixes #17050'; base main; no milestone and no review as of 2026-11-20) changes FunctionScoreQuery.java, DoubleValues.java and DoubleValuesSource.java. The pull request's change of FunctionScoreQuery's max score also clamps a negative bound: this defect. On a build of the pull request as of 2026-11-19 our repro passes, with and without assertions; it fails on main f2b061406c4b and on branch_11x 75eea467ac52. Neither item cites this list. They are not an earlier report of it: this row has been in the site's repository since its first commit of 2026-10-27, and both were opened on 2026-11-19. This site files nothing upstream.

## What it shows

You index a numeric doc-values field with a skip index (NumericDocValuesField.indexedField("delta", v)) and search BooleanQuery(SHOULD FunctionScoreQuery(MatchAllDocsQuery, DoubleValuesSource.fromLongField("delta")), SHOULD TermQuery(body:b)) for the top hit, expecting the same hit as an exhaustive search, because Lucene's MIGRATE.md documents that FunctionScoreQuery maps negative values to 0 (LUCENE-7996). Instead, with 5,000 documents where doc 0 has delta 1 and all others have -1000, doc 4999 has body:b and every other document has body:x, TOP_SCORES and the plain IndexSearcher.search(q, 1) return doc 0 (score 1.0) instead of doc 4999. The cause is that the scorer's getMaxScore returns innerMaxScore * valueMaxScore * boost without the clamp at 0 that score() applies, so a block of negative values reports a bound of -1000 for documents that all score 0, and MaxScoreBulkScorer skips windows that hold competitive documents.

The test shows that a top-k disjunction with a `FunctionScoreQuery` clause returns no hit for a matching document whose skip-indexed field value is negative (1 doc, `body:b`, `delta = -1000`). It asserts the number of hits only; that the clause scores the document 0 and reports a `getMaxScore` of -1000 was measured separately.

## Expected vs actual

Expected `IndexSearcher.search(q, 1)` to return the document (term score + 0); actual: no hit (`expected:<1> but was:<0>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/queries/function/TestFunctionScoreQueryNegativeValueMaxScore.java](src/org/apache/lucene/queries/function/TestFunctionScoreQueryNegativeValueMaxScore.java) (JUnit test)

## Run it

- Tested on Lucene main 82cf7465bb8f (a commit of 2026-11-26): still reproduces.
- Tested on Lucene main 645d4fe14a20 (a commit of 2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
- This folder holds the registered repro, which shows the missing hit (one document with delta = -1000). The tests of the second consequence in the title, a search that does not return, are not shipped here: one document with delta = -1 and the term clause boosted by 10 (update of 2026-11-15); 6,000 or 1,002 documents with two SHOULD function clauses, where the minimum competitive score is already positive and the search does not return with assertions on either (update of 2026-11-16; in the runs that did not return, the size of the negative bound was between about 1 billion and about 10^15 times that score); and 4,097 documents with two MUST function clauses, the clause over the negative field written second, where the search does not return with assertions on or off (second part of that update). The finding page quotes the 1,002-document and the 4,097-document test in full, with their commands and output, in its collapsed block "Mechanism, terms and numbers" ("Shape 2" and "Shape 3"), and describes the one-document and the 6,000-document test there ("Shape 1" and "Shape 2"); the Affected versions text of its finding record gives the shape of each.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual, or names the exception that was thrown).
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
     execute --select-class org.apache.lucene.queries.function.TestFunctionScoreQueryNegativeValueMaxScore
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on branch_11x the build takes the default from the seed).

Formal models and scripts this finding's part of the hunt used: [../../models/function-queries-bmw/](../../models/function-queries-bmw/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
