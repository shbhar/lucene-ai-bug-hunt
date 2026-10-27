# Byte-vector similarity kernels wrap their int accumulators from 33,026 dimensions (squareDistance and the uint8 kernels) and from 131,072 dimensions (dotProduct, cosine), below the 2^18 / 2^16 no-overflow bounds their code comments state

Bug LFV-138 · Area: util / byte-vector similarity kernels (VectorUtil, both vectorization providers) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call VectorUtil.squareDistance, uint8SquareDistance or cosine on two equal-length byte vectors, or score them with VectorSimilarityFunction.EUCLIDEAN or COSINE, at a dimension below the 'will not overflow if dim &lt; 2^18' bound (or 'dim &lt; 2^16' for uint8) that the code comments in DefaultVectorUtilSupport state. You expect the exact sum of squared differences, cosine(v, v) = 1, and closer vectors to score higher. Instead every provider (scalar and Panama) adds up in a 32-bit int that wraps: from 33,026 dimensions the squared distance comes back negative (-2,147,451,646 instead of 2,147,515,650), from 131,072 dimensions cosine(v, v) is -1.0, and at 66,052 dimensions EUCLIDEAN scores the farthest possible vector above an adjacent one.

## Expected vs actual

- Expected: You expect the exact sum of squared differences, cosine(v, v) = 1, and closer vectors to score higher.
- Actual: Every provider (scalar and Panama) adds up in a 32-bit int that wraps: from 33,026 dimensions the squared distance comes back negative (-2,147,451,646 instead of 2,147,515,650), from 131,072 dimensions cosine(v, v) is -1.0, and at 66,052 dimensions EUCLIDEAN scores the farthest possible vector above an adjacent one.

## Files

- [src/org/apache/lucene/util/TestByteKernelOverflowBound.java](src/org/apache/lucene/util/TestByteKernelOverflowBound.java) (JUnit test)
- [src2/org/apache/lucene/util/TestByteKernelOverflowBound.java](src2/org/apache/lucene/util/TestByteKernelOverflowBound.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.TestByteKernelOverflowBound
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.TestByteKernelOverflowBound
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/vectorutil-providers/](../../models/vectorutil-providers/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
