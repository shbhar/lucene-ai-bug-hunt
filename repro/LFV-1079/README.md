# Function-query vector similarity value sources (Float/Byte/Float16VectorSimilarityFunction) leave the similarity function out of equals but hash it, so EUCLIDEAN and COSINE sources over the same vectors compare equal with different hash codes

Finding LFV-1079 · Area: queries / function queries: valuesource.VectorSimilarityFunction equality (base of Float/Byte/Float16VectorSimilarityFunction, and so of the FunctionQuery, FunctionRangeQuery and FunctionScoreQuery that wrap them) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1079.html](../../bugs/LFV-1079.html)

AI-assisted finding, confirmed by automated checks. Upstream status: Fix in progress, by sjs004 (PR #16974) on 2026-11-03 (https://github.com/apache/lucene/pull/16974). PR #16974 references this bug. This site files nothing upstream.

## What it shows

You build two function-query value sources over the same vector field and the same query vector, one with EUCLIDEAN and one with COSINE (new FloatVectorSimilarityFunction(EUCLIDEAN, field, target) and the same with COSINE), and expect them to be unequal because they compute different scores; the class itself treats the function as part of its identity, since hashCode() and description() include it. Instead equals() compares only the two vector sources and returns true, while hashCode() returns different values, which breaks the java.lang.Object rule that equal objects have equal hash codes. The Byte and Float16 subclasses share this equals, and FunctionQuery and the other wrappers pass the false equality on.

## Expected vs actual

- Expected: The EUCLIDEAN and the COSINE source are not equal, so `assertNotEquals` passes.
- Actual: `equals()` returns true: `AssertionError: Values should be different. Actual: COSINE(FloatKnnVectorFieldSource(v), ConstKnnFloatValueSource([1.0, 2.0]))`, with assertions on and off.

## Files

- [src/org/apache/lucene/queries/function/TestVectorSimilarityFunctionEquals.java](src/org/apache/lucene/queries/function/TestVectorSimilarityFunctionEquals.java) (JUnit test)

## Run it

- Tested on Lucene main e1faeba08bde (2026-11-02): still reproduces.
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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.function.TestVectorSimilarityFunctionEquals
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestVectorSimilarityFunctionEquals.java](TestVectorSimilarityFunctionEquals.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
