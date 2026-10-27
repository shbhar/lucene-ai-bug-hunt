# FunctionRangeQuery scores a document with its raw, possibly negative, function value although LUCENE-7996 forbids negative scores and FunctionQuery over the same source clamps them to 0; since 10.1.0 a matching document whose value is -Infinity can then disappear from the top-k results of a minimumShouldMatch &gt;= 2 BooleanQuery

Bug LFV-406 · Area: queries / function queries (ValueSourceScorer behind FunctionRangeQuery, LUCENE-7996 non-negative score rule; the hit loss surfaces in core WANDScorer) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a document with DoubleDocValuesField("d", -5.0) and search new FunctionRangeQuery(new DoubleFieldSource("d"), -10.0, 10.0, true, true), expecting a non-negative score, because since LUCENE-7996 (8.0) scorers must not produce negative scores and FunctionQuery over the same source scores that document 0. Instead it scores -5.0 (-Infinity and NaN become -Float.MAX_VALUE), because ValueSourceScorer.score returns the raw value and LUCENE-7996 clamped FunctionQuery, BoostedQuery and FunctionScoreQuery but not this scorer. Since 10.1.0 this also loses hits: with minimumNumberShouldMatch = 2 over SHOULD clauses FunctionRangeQuery(d, open range), body:a and body:b (sparse term clauses; also dense ones since 10.5.0), a matching document whose value is -Infinity is missing from search(q, 10), whose totalHits says '3 hits' while count(q) is 4, because WANDScorer rejects a candidate whose scaled score is below the initial minimum competitive score of 0.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: It scores -5.0 (-Infinity and NaN become -Float.MAX_VALUE), because ValueSourceScorer.score returns the raw value and LUCENE-7996 clamped FunctionQuery, BoostedQuery and FunctionScoreQuery but not this scorer.

## Files

- [src/org/apache/lucene/queries/function/TestFunctionRangeQueryNegativeScore.java](src/org/apache/lucene/queries/function/TestFunctionRangeQueryNegativeScore.java) (JUnit test)

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
     execute --select-class org.apache.lucene.queries.function.TestFunctionRangeQueryNegativeScore
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
