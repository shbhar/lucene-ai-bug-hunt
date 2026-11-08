# LateInteractionField and LateInteractionFloatValuesSource accept all-zero (and non-finite) token vectors, so late-interaction rescoring under its default COSINE gives the document a NaN score (AssertionError in VectorUtil.cosine with -ea)

Finding LFV-1120 · Area: core / search: late-interaction rescoring (LateInteractionField, LateInteractionFloatValuesSource, LateInteractionRescorer) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1120.html](../../bugs/LFV-1120.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a document with a LateInteractionField whose token matrix is {{1,0},{0,0}} (the second token is all zeros) and rescore it with LateInteractionRescorer.create("li", {{1,0}}), which uses COSINE by default. You expect a valid score, here 1.0 because the document's first token equals the query token, or an IllegalArgumentException for the zero token, as KnnFloatVectorField gives under COSINE. Instead the field accepts the zero row, the cosine of the zero row is 0/0 = NaN, Float.max in SUM_MAX_SIM keeps that NaN over the exact match, and the document comes back with score NaN (with -ea, VectorUtil.cosine throws AssertionError).

## Expected vs actual

- Expected: A finite score, here 1.0 (or a clean `IllegalArgumentException` for the zero token).
- Actual: The score is NaN with assertions off; with assertions on, an `AssertionError` at `VectorUtil.cosine(VectorUtil.java:105)`.

## Files

- [src/org/apache/lucene/search/TestLateInteractionZeroTokenVector.java](src/org/apache/lucene/search/TestLateInteractionZeroTokenVector.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestLateInteractionZeroTokenVector
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestLateInteractionZeroTokenVector.java](TestLateInteractionZeroTokenVector.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
