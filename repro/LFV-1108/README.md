# FunctionRangeQuery on a string field (BytesRefFieldSource / SortedSetFieldSource) compares term ordinals as float, so in a segment with more than 2^24 distinct values of the field it also matches values just outside the range bounds

Finding LFV-1108 · Area: queries / function queries (DocTermsIndexDocValues.getRangeScorer: string ValueSource range matching behind FunctionRangeQuery) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run FunctionRangeQuery(new BytesRefFieldSource("f"), "016777217", null, true, true) as a filter on a segment whose SORTED field f has more than 2^24 distinct values, and expect only documents whose value is at least "016777217". The document holding "016777216" matches too, with no error: getRangeScorer turns the string bounds into exact int ordinals but then compares each document's ordinal as a float, and above 2^24 neighbouring ordinals round to the same float. Only values within one float step of a bound are wrongly added; no in-range document is lost.

## Expected vs actual

- Expected: 0 hits: the only value, `016777216`, sorts before the lower bound `016777217`.
- Actual: 1 hit, no error: `AssertionError: frange(f):[016777217 TO *] expected:<0> but was:<1>`, with assertions on and off.

## Files

- [src/org/apache/lucene/queries/function/TestFunctionRangeQueryLargeOrdinals.java](src/org/apache/lucene/queries/function/TestFunctionRangeQueryLargeOrdinals.java) (JUnit test)

## Run it

- Tested on Lucene main e1faeba08bde (2026-11-02): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.function.TestFunctionRangeQueryLargeOrdinals
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this finding's part of the hunt used: [../../models/docterms-range-scorer-float-ordinals-above-2p24/](../../models/docterms-range-scorer-float-ordinals-above-2p24/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
