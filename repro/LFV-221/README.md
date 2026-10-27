# DoubleValuesSource rounds the skip-index block bound to the nearest float, so with a boost FunctionScoreQuery's max score can be one ulp below a real score and top-k search returns the wrong top hit

Bug LFV-221 · Area: search / function scoring (DoubleValuesSource skip-index score bounds for FunctionScoreQuery block-max pruning) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a long counter with the doc-values skip index (NumericDocValuesField.indexedField('count', v)) and search BooleanQuery(SHOULD BoostQuery(FunctionScoreQuery(MatchAllDocsQuery, DoubleValuesSource.fromLongField('count')), 1.5f), SHOULD TermQuery(body:b)), expecting the same top hit as an exhaustive search. Instead, with 5,000 docs where doc 0 holds 2^24 and doc 4999 holds 2^24 + 1, the pruned search (TOP_SCORES, which plain IndexSearcher.search(q, 1) also uses) returns doc 0 (score 25165824) instead of doc 4999 (score 25165826). The cause is that the block bound is the decoded block maximum cast to the nearest float (2^24 + 1 becomes 2^24) and FunctionScoreQuery multiplies that rounded float by the boost, so the block's bound (25165824) is one float step below the score it must cover and the block is skipped; ordinary doubles do the same (0.065 with boost 1.1 scores 0.0715 under a bound of 0.071499996).

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: With 5,000 docs where doc 0 holds 2^24 and doc 4999 holds 2^24 + 1, the pruned search (TOP_SCORES, which plain IndexSearcher.search(q, 1) also uses) returns doc 0 (score 25165824) instead of doc 4999 (score 25165826).

## Files

- [src/org/apache/lucene/queries/function/TestFunctionScoreQueryMaxScoreRounding.java](src/org/apache/lucene/queries/function/TestFunctionScoreQueryMaxScoreRounding.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.function.TestFunctionScoreQueryMaxScoreRounding
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/function-queries-bmw/](../../models/function-queries-bmw/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
