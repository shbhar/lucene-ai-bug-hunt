# FunctionScoreQuery scores negative values as 0, but its block-max bound stays negative, so top-k search skips competitive documents

Bug LFV-203 · Area: queries / FunctionScoreQuery block-max pruning (#16431) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a numeric doc-values field with a skip index (NumericDocValuesField.indexedField("delta", v)) and search BooleanQuery(SHOULD FunctionScoreQuery(MatchAllDocsQuery, DoubleValuesSource.fromLongField("delta")), SHOULD TermQuery(body:b)) for the top hit, expecting the same hit as an exhaustive search, because FunctionScoreQuery documents that a negative value simply scores 0 (MIGRATE.md, LUCENE-7996). Instead, with 5,000 documents where doc 0 has delta 1, all others have -1000, and only doc 4999 matches body:b, TOP_SCORES and the plain IndexSearcher.search(q, 1) return doc 0 (score 1.0) instead of doc 4999. The cause is that the scorer's getMaxScore returns innerMaxScore * valueMaxScore * boost without the clamp at 0 that score applies, so a block of negative values reports a bound of -1000 for documents that all score 0, and MaxScoreBulkScorer skips windows that hold competitive documents.

The test shows that a `FunctionScoreQuery` whose skip-indexed field value is negative (scored as 0) reports a negative `getMaxScore`, so a top-k disjunction skips a matching document (1 doc, `body:b`, `delta = -1000`).

## Expected vs actual

Expected `IndexSearcher.search(q, 1)` to return the document (term score + 0); actual: no hit (`expected:<1> but was:<0>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/queries/function/TestFunctionScoreQueryNegativeValueMaxScore.java](src/org/apache/lucene/queries/function/TestFunctionScoreQueryNegativeValueMaxScore.java) (JUnit test)

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
     execute --select-class org.apache.lucene.queries.function.TestFunctionScoreQueryNegativeValueMaxScore
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/function-queries-bmw/](../../models/function-queries-bmw/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
