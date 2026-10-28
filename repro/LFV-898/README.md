# FeatureField saturation and sigmoid queries and IntervalQuery scoring compute w*(1 - k/(S+k)) instead of the documented w*S/(S+k) (and the same rewrite for the sigmoid), so documents whose value is far below the pivot score exactly 0.0 or tie with documents that have different values

Bug LFV-898 · Area: document / FeatureField score functions (SaturationFunction and SigmoidFunction behind newSaturationQuery and newSigmoidQuery); queries / intervals IntervalScoreFunction (IntervalQuery's default saturation scoring and its pivot and pivot-plus-exponent constructors) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You rank with FeatureField.newSaturationQuery, whose javadoc says it 'will score documents as weight * S / (S + pivot)' (newSigmoidQuery and IntervalQuery's scoring state the same kind of formula), a value that is positive and grows with S, so a document with a smaller feature value should score lower but above 0. The code instead computes weight * (1 - pivot / (S + pivot)) (and 1 - k^a / (S^a + k^a) for the sigmoid) so that rounding cannot make scores decrease, and when S is far below the pivot the sum S + pivot rounds to almost exactly the pivot and the subtraction cancels. With pivot 1000 a document with S = 1e-5 scores exactly 0.0 while explain prints 'computed as w * S / (S + k)', with the library's default pivot 199 adjacent pairs of distinct stored values tie, and IntervalQuery gives intervals 1,000,000 and 1,030,000 positions wide the same score and a 2^25-wide interval 0.0.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/document/TestFeatureFieldScoreCancellation.java](src/org/apache/lucene/document/TestFeatureFieldScoreCancellation.java) (JUnit test)
- [src/org/apache/lucene/queries/intervals/TestIntervalQueryScoreCancellation.java](src/org/apache/lucene/queries/intervals/TestIntervalQueryScoreCancellation.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
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
     execute --select-class org.apache.lucene.document.TestFeatureFieldScoreCancellation \
       --select-class org.apache.lucene.queries.intervals.TestIntervalQueryScoreCancellation
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/feature-saturation-sigmoid-cancellation/](../../models/feature-saturation-sigmoid-cancellation/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
