# FunctionRangeQuery over a double or float source drops -Infinity / +Infinity documents when an omitted (null) bound is marked exclusive ({* TO x] or [x TO *}), while int, long, enum and string sources treat an omitted bound as unbounded

Bug LFV-416 · Area: queries / function queries (FunctionRangeQuery range scorers: DoubleDocValues.getRangeScorer and the float default FunctionValues.getRangeScorer) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index documents whose DoubleDocValuesField "d" holds -Infinity, 0 and +Infinity and search new FunctionRangeQuery(new DoubleFieldSource("d"), null, 5.0, false, true), which prints as frange(double(d)):{* TO 5.0]; you expect every value up to 5, -Infinity included, because the same query over a LongFieldSource keeps the Long.MIN_VALUE document and Lucene treats a null range endpoint as open (TermRangeQuery: 'Open endpoints may not be exclusive', normalized to inclusive). Instead only the 0 document matches: DoubleDocValues.getRangeScorer (and the float default in FunctionValues.getRangeScorer) replaces the null bound with -Infinity and then applies the exclusive flag to it (docVal &gt; -Infinity), so the -Infinity document is dropped, and [0 TO *} drops the +Infinity document the same way.

## Expected vs actual

- Expected: You expect every value up to 5, -Infinity included, because the same query over a LongFieldSource keeps the Long.MIN_VALUE document and Lucene treats a null range endpoint as open (TermRangeQuery: 'Open endpoints may not be exclusive', normalized to inclusive).
- Actual: Only the 0 document matches: DoubleDocValues.getRangeScorer (and the float default in FunctionValues.getRangeScorer) replaces the null bound with -Infinity and then applies the exclusive flag to it (docVal &gt; -Infinity), so the -Infinity document is dropped, and [0 TO *} drops the +Infinity document the same way.

## Files

- [src/org/apache/lucene/queries/function/TestFunctionRangeOpenBoundInfinity.java](src/org/apache/lucene/queries/function/TestFunctionRangeOpenBoundInfinity.java) (JUnit test)

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
     execute --select-class org.apache.lucene.queries.function.TestFunctionRangeOpenBoundInfinity
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
