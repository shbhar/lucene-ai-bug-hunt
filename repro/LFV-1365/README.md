# ValueSource#asLongValuesSource() always reports needsScores() false although it feeds the passed scores to the wrapped ValueSource, so a sort on a score-dependent ValueSource (for example a function over ValueSource.fromDoubleValuesSource(DoubleValuesSource.SCORES)) runs without scores and silently returns sort values not computed from the real score (all 0 in the two-document repro) and hits in doc-id order (since 6.4.0)

Finding LFV-1365 · Area: queries / function queries (ValueSource to LongValuesSource bridge: ValueSource#asLongValuesSource, WrappedLongValuesSource.needsScores, consumed by LongValuesSource#getSortField) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1365.html](../../bugs/LFV-1365.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a ValueSource that depends on the score, new LinearFloatFunction(ValueSource.fromDoubleValuesSource(DoubleValuesSource.SCORES), 1000f, 0f) (1000 x score), expose it with asLongValuesSource() and sort a TermQuery's hits on its getSortField(true); you expect the hits in score order with sort values 1000 x score ([doc1=132, doc0=68]), because LongValuesSource#needsScores is documented as 'Return true if document scores are needed to calculate values' and asDoubleValuesSource() of the same ValueSource answers true. Instead the long bridge (ValueSource.WrappedLongValuesSource) answers needsScores() false (ValueSource.java:156) although its getValues hands the passed scores to the wrapped source under the 'scorer' context key (:130-131); LongValuesSortField forwards that false to Sort.needsScores(), so the search collects without scores and every hit gets sort value 0, in doc-id order ([doc0=0, doc1=0]), with no exception.

## Expected vs actual

- Expected: The hit's sort value is its score as a long: `3`.
- Actual: `0` with seed `DEADBEEF`: `AssertionError: expected:<3> but was:<0>`, with assertions on and off and no other error; `1` with some other seeds (for example `5EED0003`); never 3.

## Files

- [src/org/apache/lucene/queries/function/TestLongBridgeNeedsScores.java](src/org/apache/lucene/queries/function/TestLongBridgeNeedsScores.java) (JUnit test)

## Run it

- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
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

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.function.TestLongBridgeNeedsScores
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestLongBridgeNeedsScores.java](TestLongBridgeNeedsScores.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
