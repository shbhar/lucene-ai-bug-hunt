# Quantized MIP/EUCLIDEAN scores become NaN when OSQ correction terms overflow

Bug LFV-064 · Area: codecs / vectors · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

With the OSQ format and MAXIMUM_INNER_PRODUCT or EUCLIDEAN, you index vectors with components around 1e17-1e19. You expect finite scores, as the float path gives. Instead the correction terms overflow to inf - inf, the quantized score is NaN, and NaN drops whole 64-document batches (one outlier made 301 of 301 scores NaN).

The test shows that with the flat Lucene104ScalarQuantizedVectorsFormat and EUCLIDEAN, a 16-dim vector with one component -1e19 (all float quantities finite) gets a NaN quantized score, and a kNN query for that vector finds nothing.

## Expected vs actual

Expected: doc 0 found with a finite score (the float format gives 1.0). Actual (-ea and -da): score NaN (Inf - Inf at Lucene104ScalarQuantizedVectorScorer.java:291) and 0 hits.

## Files

- [src/org/apache/lucene/codecs/lucene104/TestScalarQuantizedEuclideanOverflow.java](src/org/apache/lucene/codecs/lucene104/TestScalarQuantizedEuclideanOverflow.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.codecs.lucene104.TestScalarQuantizedEuclideanOverflow
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/study-quantization-smt/](../../models/study-quantization-smt/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
