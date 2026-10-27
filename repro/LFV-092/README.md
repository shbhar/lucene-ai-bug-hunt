# ConstKnnFloat16ValueSource accepts Inf/NaN float16 query vectors that its float32 twin ConstKnnFloatValueSource rejects (missing VectorUtil.checkFiniteFloat16)

Bug LFV-092 · Area: queries / function vector sources · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a ConstKnnFloat16ValueSource from a float16 query vector that contains an infinity or NaN half and score it against a KnnFloat16VectorField with a FunctionQuery over Float16VectorSimilarityFunction. You expect the constructor to throw IllegalArgumentException, as its float32 twin ConstKnnFloatValueSource does (VectorUtil.checkFinite) and as KnnFloat16VectorQuery and KnnFloat16VectorField do for the same halves (VectorUtil.checkFiniteFloat16). Instead the constructor only null-checks the array, so with -ea scoring later fails with a bare AssertionError inside VectorUtil (EUCLIDEAN, DOT_PRODUCT, MAXIMUM_INNER_PRODUCT; COSINE silently scores 0), and with -da documents score 0 through FunctionQuery's intended NaN/negative clamp or, for DOT_PRODUCT and MAXIMUM_INNER_PRODUCT with an infinite component, +Infinity.

## Expected vs actual

- Expected: You expect the constructor to throw IllegalArgumentException, as its float32 twin ConstKnnFloatValueSource does (VectorUtil.checkFinite) and as KnnFloat16VectorQuery and KnnFloat16VectorField do for the same halves (VectorUtil.checkFiniteFloat16).
- Actual: The constructor only null-checks the array, so with -ea scoring later fails with a bare AssertionError inside VectorUtil (EUCLIDEAN, DOT_PRODUCT, MAXIMUM_INNER_PRODUCT; COSINE silently scores 0), and with -da documents score 0 through FunctionQuery's intended NaN/negative clamp or, for DOT_PRODUCT and MAXIMUM_INNER_PRODUCT with an infinite component, +Infinity.

## Files

- [src/org/apache/lucene/queries/function/TestConstKnnFloat16ValueSourceValidation.java](src/org/apache/lucene/queries/function/TestConstKnnFloat16ValueSourceValidation.java) (JUnit test)

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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.function.TestConstKnnFloat16ValueSourceValidation
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
