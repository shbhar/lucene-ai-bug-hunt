# DoubleValues.withDefault's getMaxScore returns the wrapped source's bound, which does not cover missingValue, so a FunctionScoreQuery over such a source can silently drop the true top hit under TOP_SCORES

Bug LFV-214 · Area: search / function scoring (DoubleValues.withDefault score bound for FunctionScoreQuery block-max pruning, #16431) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You write a DoubleValuesSource that returns DoubleValues.withDefault(DoubleValuesSource.fromLongField("price").getValues(ctx, scores), 100) over a price field indexed with a skip index (NumericDocValuesField.indexedField), and search BooleanQuery(SHOULD FunctionScoreQuery(MatchAllDocsQuery, thatSource), SHOULD TermQuery(tag:t)) for the top hit, expecting the document without a price (which scores the default 100) to win, as it does in an exhaustive search. Instead, with 12,288 documents (prices i % 10, doc 0 = 50, doc 9000 without a price), the plain IndexSearcher.search(q, 1) returns doc 0 (score 50.0018) instead of doc 9000 (score 100). The cause is that withDefault.getMaxScore returns the wrapped source's bound, the skip-index block maximum, which counts only documents that have a value (9 in doc 9000's block), although withDefault itself produces missingValue (100) for documents without one, so pruning skips the block that holds the true top hit.

The test shows that `DoubleValues.withDefault(fromLongField("price") values, 100)` produces 100 for a document without a price, but its `getMaxScore` forwards the wrapped skip-index bound, which only covers documents that have a value (2 documents, one block).

## Expected vs actual

Expected `getMaxScore(1) >= 100` (the value doc 0 produces); actual: `doc 0 value 100.0 > getMaxScore(1) = 1.0`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/search/TestDoubleValuesWithDefaultMaxScore.java](src/org/apache/lucene/search/TestDoubleValuesWithDefaultMaxScore.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.TestDoubleValuesWithDefaultMaxScore
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/function-queries-bmw/](../../models/function-queries-bmw/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
