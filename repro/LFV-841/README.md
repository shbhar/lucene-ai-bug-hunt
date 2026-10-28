# On main, a 9.x/10.x int7/int4 scalar-quantized segment whose vectors are all identical (stored quantiles equal) is decoded with the min == max widening that #14049 added, not the quantizer that wrote it, so its vectors read 127 (int7) or 15 (int4) units lower in every dimension and exact matches score about 0 and rank last (unreleased, 11.0 only)

Bug LFV-841 · Area: codecs / legacy scalar-quantized vectors (util/quantization ScalarQuantizer, backward-codecs Lucene99ScalarQuantizedVectorsReader) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a field with the Lucene99 HNSW scalar-quantized format (int7, EUCLIDEAN) on 10.3.2 or 9.12.3, one segment holds only documents whose vector is [1,1,1,1], so the writer stores the quantiles (1, 1) and every code 0; you then open the unchanged index on main and search for [1,1,1,1]. Every release reader tested (9.9.2 to 10.5.2) decodes code 0 as 1.0, as the ScalarQuantizer formula for the stored quantiles says, and ranks those exact matches first with score 1.0. On main the reader rebuilds the quantizer through the min == max widening added by #14049, decodes those vectors as [-126,-126,-126,-126], and the exact matches score 1.5e-5 and rank last behind a document with half the similarity; int4 fields decode 15 units low, and DOT_PRODUCT and COSINE exact matches score 0.0.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/backward_index/TestOldUniformQuantizedSegment.java](src/org/apache/lucene/backward_index/TestOldUniformQuantizedSegment.java) (JUnit test)
- [src/GenUniformSqIndex.java](src/GenUniformSqIndex.java) (main() class)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual); a main() class that exits non-zero, with an exception or AssertionError, is the bug reproducing.
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/backward-codecs/build/libs/lucene-backward-codecs-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.backward_index.TestOldUniformQuantizedSegment

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" GenUniformSqIndex
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/legacy-int7-int4-scalar-quantized-scoring-fp-smt/](../../models/legacy-int7-int4-scalar-quantized-scoring-fp-smt/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
