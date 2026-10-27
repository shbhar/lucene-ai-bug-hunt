# FunctionScoreQuery and FunctionQuery score a function value of -0.0 as -0.0f instead of 0, because their LUCENE-7996 negative-value clamps test value &gt;= 0; since 10.2.0 the top-1 hit can then differ from the first of the top-2 hits

Bug LFV-273 · Area: queries / function scoring (FunctionScoreQuery and FunctionQuery negative-value clamps, LUCENE-7996; visible through core TopScoreDocCollector tie handling) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index doc 0 with FloatDocValuesField("f", -0f) and doc 1 with FloatDocValuesField("f", 0f) and search new FunctionScoreQuery(new TermQuery(k:x), DoubleValuesSource.fromFloatField("f")) or new FunctionQuery(new FloatFieldSource("f")), expecting doc 0 to score 0 like any negative function value since LUCENE-7996 ('Scorers are no longer allowed to produce negative scores'; the test framework's AssertingScorer checks Float.compare(score, 0f) &gt;= 0) and the two zero-scored hits to tie in doc-id order. Instead doc 0 scores -0.0f, because the clamps test 'factor &gt;= 0' (FunctionScoreQuery) and 'val &gt;= 0 == false' (FunctionQuery) and -0.0 &gt;= 0 is true in Java; explain does not truncate it either. Since 10.2.0, search(q, 1) returns doc 0 while search(q, 2) returns [doc 1, doc 0], because TopScoreDocCollector's 'score &lt;= topScore' test treats the two zeros as equal while its heap and HitQueue order -0.0 below 0.0.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: Doc 0 scores -0.0f, because the clamps test 'factor &gt;= 0' (FunctionScoreQuery) and 'val &gt;= 0 == false' (FunctionQuery) and -0.0 &gt;= 0 is true in Java; explain does not truncate it either.

## Files

- [src/org/apache/lucene/queries/function/TestFunctionScoreNegativeZero.java](src/org/apache/lucene/queries/function/TestFunctionScoreNegativeZero.java) (JUnit test)

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
     execute --select-class org.apache.lucene.queries.function.TestFunctionScoreNegativeZero
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
