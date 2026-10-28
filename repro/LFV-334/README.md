# DoubleValuesSourceRescorer.rescore passes null scores to a DoubleValuesSource whose needsScores is true, so rescoring with DoubleValuesSource.SCORES, an expression that reads _score or a FunctionScoreQuery.boostByValue source throws (AssertionError with -ea; NullPointerException or a misleading 'Unrecognized variable (_score)' RuntimeException with -da), while explain on the same input returns normally

Bug LFV-334 · Area: core / search: DoubleValuesSourceRescorer.rescore and explain (score-dependent DoubleValuesSource) · Priority: medium · Impact: crash · Novelty: new

Bug page: [../../bugs/LFV-334.html](../../bugs/LFV-334.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You subclass DoubleValuesSourceRescorer to re-rank first-pass hits with a source that uses the query score, for example DoubleValuesSource.SCORES or the expression '_score * 2' with _score bound to SCORES, and expect rescore to work as it does for any other source: the constructor accepts any DoubleValuesSource and documents no restriction. Instead rescore calls source.getValues(ctx, null) for every source (DoubleValuesSourceRescorer.java:73), although DoubleValuesSource#getValues allows a null scores argument only when needsScores is false and the SCORES javadoc says callers must not pass null, so it throws on the first hit: an AssertionError with -ea, and with -da a NullPointerException at line 74 or, for the expression, RuntimeException 'Unrecognized variable (_score) referenced in expression (_score * 2).'. explain for the same rescorer, source and document returns a normal explanation, because it passes a placeholder 'DoubleValuesSource was not initialized with query scores' explanation instead of scores.

## Expected vs actual

- Expected: `rescore` returns doc 1 = 3.4472, doc 2 = 1.9486, doc 0 = 0.4721, that is `sqrt(first-pass score) + ln(popularity)` for each hit.
- Actual: `rescore` throws on the first hit: `AssertionError` with assertions on, `RuntimeException: Unrecognized variable (_score) referenced in expression (sqrt(_score) + ln(popularity)).` with them off.

## Files

- [src/org/apache/lucene/search/TestDoubleValuesSourceRescorerScoreSource.java](src/org/apache/lucene/search/TestDoubleValuesSourceRescorerScoreSource.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestDoubleValuesSourceRescorerScoreSource
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestDoubleValuesSourceRescorerScoreExpression.java](TestDoubleValuesSourceRescorerScoreExpression.java)
- [TestDoubleValuesSourceRescorerScores.java](TestDoubleValuesSourceRescorerScores.java)
- [TestDoubleValuesSourceRescorerBoostSource.java](TestDoubleValuesSourceRescorerBoostSource.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
