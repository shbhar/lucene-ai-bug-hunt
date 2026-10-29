# FunctionRangeQuery over ValueSource.fromDoubleValuesSource(...) compares bounds and values as floats, not doubles, so double and long ranges silently gain or lose hits near a bound and beyond Float.MAX_VALUE

Bug LFV-409 · Area: queries / function queries (the ValueSource.fromDoubleValuesSource bridge: its FunctionValues inherits the float default FunctionValues.getRangeScorer that FunctionRangeQuery calls) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-409.html](../../bugs/LFV-409.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index DoubleDocValuesField("d") values 0.9999999999999999, 1.0 and 1.0000000000000002 and search new FunctionRangeQuery(ValueSource.fromDoubleValuesSource(DoubleValuesSource.fromDoubleField("d")), 1.0, 1.0, true, true), expecting only the document whose value is 1.0, as you get with new DoubleFieldSource("d") or with FunctionMatchQuery over the same DoubleValuesSource. Instead all three match, because the bridged FunctionValues keeps the exact double in doubleVal but does not override getRangeScorer, so it inherits the FunctionValues default that parses both bounds with Float.parseFloat and compares the value cast to float. Likewise {1.0 TO *] misses 1.0000000000000002, [* TO 1e300] matches 1e301, [16777217 TO 16777217] on a long field also matches 16777216, and [* TO 1700000000000] over epoch milliseconds also matches 1700000060000, all without an error.

The test shows that FunctionRangeQuery over ValueSource.fromDoubleValuesSource(DoubleValuesSource.fromDoubleField("d")) compares the value and the bounds as floats, so [1.0 TO 1.0] matches a document whose value is 1.0000000000000002.

## Expected vs actual

Expected 0 hits; actual 1 hit (`expected:<0> but was:<1>`), with -ea and -da.

## Files

- [src/org/apache/lucene/queries/function/TestFromDoubleValuesSourceRange.java](src/org/apache/lucene/queries/function/TestFromDoubleValuesSourceRange.java) (JUnit test)

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
     execute --select-class org.apache.lucene.queries.function.TestFromDoubleValuesSourceRange
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/range-bound-cross-path/](../../models/range-bound-cross-path/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
