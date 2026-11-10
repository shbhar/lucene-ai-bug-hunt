# QuantizedByteVectorValues.getCorrectiveTerms' javadoc calls the third EUCLIDEAN corrective term 'the l2norm of the centered vector', but OptimizedScalarQuantizer stores, and Lucene104ScalarQuantizedVectorScorer requires, the squared l2norm, so an implementer who supplies the documented value gets wrong EUCLIDEAN scores from Lucene's public scorer (1/6 instead of 1/26 for a centered vector (3, 4)); javadoc only, the code is intended; since 10.4.0

Finding LFV-1450 · Area: core / vector quantization (util.quantization QuantizedByteVectorValues.getCorrectiveTerms javadoc vs OptimizedScalarQuantizer and Lucene104ScalarQuantizedVectorScorer) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1450.html](../../bugs/LFV-1450.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You implement the public abstract QuantizedByteVectorValues for a EUCLIDEAN field and, as getCorrectiveTerms' javadoc says, return 'the l2norm of the centered vector' as the third corrective term: 5 for the vector (4, 5) with centroid (1, 1). Lucene's public Lucene104ScalarQuantizedVectorScorer then scores the query (7, 9), whose true squared distance to (4, 5) is 25, as 0.16666667 (1/6) instead of 0.03846154 (1/26), because it computes the squared distance as queryCorrection + indexCorrection - 2 * dot and needs the squared norm 25 there; Lucene's own quantizer indeed stores the squared norm (13.0, not sqrt(13) = 3.6055512, for a centered vector (2, 3)). The code is intended and correct; only the javadoc, which omits the word 'squared', is wrong.

## Expected vs actual

- Expected: 0.03846154 = 1 / (1 + 25), the EUCLIDEAN score for the true squared distance 25.
- Actual: `java.lang.AssertionError: expected:<0.03846154> but was:<0.16666667>` (1/6), with assertions on and off.

## Files

- [src/example/osq/TestDocumentedEuclideanCorrectiveTerm.java](src/example/osq/TestDocumentedEuclideanCorrectiveTerm.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): reproduced.
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

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class example.osq.TestDocumentedEuclideanCorrectiveTerm
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestDocumentedEuclideanCorrectiveTerm.java](TestDocumentedEuclideanCorrectiveTerm.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
