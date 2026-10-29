# DoubleValuesSource.fromDoubleField/fromFloatField declare their raw IEEE-bit decoders INCREASING, so on a skip-indexed raw-bits field one NaN makes a block's score bound NaN (FunctionScoreQuery under TOP_SCORES then silently drops the true top hit) and all-negative blocks get inverted bounds

Bug LFV-189 · Area: search / function scoring (DoubleValuesSource skip-index score bounds for FunctionScoreQuery block-max pruning) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index doubles as raw IEEE bits in a NUMERIC doc-values field with a skip index (NumericDocValuesField.indexedField('rating', Double.doubleToRawLongBits(v))) and search BooleanQuery(SHOULD FunctionScoreQuery(MatchAllDocsQuery, DoubleValuesSource.fromDoubleField('rating')), SHOULD TermQuery) for the top hit, expecting the same top hit as an exhaustive search (a NaN value simply scores 0, as upstream TestFunctionScoreQuery#testNaN expects). Instead, with 5,000 docs valued 0.5, one NaN at doc 4500 and 50.0 at doc 4999, TOP_SCORES returns doc 1 (score 4.19) instead of doc 4999 (score 50). The cause is that fromDoubleField declares Double::longBitsToDouble INCREASING, so a block's maximum is read as the decoded largest raw long: NaN's raw bits are above every number's, giving a NaN bound, and negative values are in reverse raw order, so a block holding {-1.0, -2.0} reports getMaxScore -2.0 and getMinScore -1.0.

The test shows that `DoubleValuesSource.fromDoubleField` reads skip-index bounds as if raw IEEE bits were ordered like doubles, so a block of {-1.0, -2.0} gets a max bound of -2.0, and one NaN in a block makes `FunctionScoreQuery` in a disjunction lose the true top hit.

## Expected vs actual

Expected: `getMaxScore >= -1.0`, and doc 4097 (rating 50) as the top hit of `search(q, 1)`. Actual: `getMaxScore = -2.0`, and doc 1 as the top hit.

## Files

- [src/org/apache/lucene/queries/function/TestRawBitsDoubleFieldBounds.java](src/org/apache/lucene/queries/function/TestRawBitsDoubleFieldBounds.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 2 test(s) run, 2 failed.
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
     execute --select-class org.apache.lucene.queries.function.TestRawBitsDoubleFieldBounds
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/function-queries-bmw/](../../models/function-queries-bmw/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
