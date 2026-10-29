# FLOAT16 vectors read back from a quantized index whose raw vectors were dropped can contain +-Infinity, so full-precision rescoring ranks an exact match last

Bug LFV-084 · Area: codecs / vectors · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index finite FLOAT16 vectors with large components (for example 60000) in the Lucene104 scalar-quantized format, then drop the raw vectors the way the format's own tests do (simulateEmptyRawVectors), so reads go through the dequantized fp16 view. You expect getFloat16VectorValues.vectorValue to return an approximation of each stored vector, and full-precision rescoring with a document's own vector to rank that document first. Instead OptimizedScalarQuantizer.deQuantize(byte, short,...) narrows a slightly out-of-range float reconstruction to fp16 without saturating, so 60000 comes back as Infinity, and rescoring gives the exact match the score 0.0 and ranks it last (with -ea, an AssertionError in VectorUtil.squareDistance).

The test shows that two finite FLOAT16 vectors, `(65504, -65504, 0)` and `(0, 0, 0)`, indexed with the default `Lucene104ScalarQuantizedVectorsFormat` and then read back through the dequantized view after the raw vectors are dropped (`simulateEmptyRawVectors`, as upstream's format test does) come back with a non-finite component.

## Expected vs actual

Expected `Float16VectorValues#vectorValue(0)` to return a finite approximation of `-65504`; actual: `-Infinity` (`OptimizedScalarQuantizer.deQuantize` narrows an out-of-range reconstruction to fp16 without saturating), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/codecs/lucene104/TestFloat16DequantizedViewOverflow.java](src/org/apache/lucene/codecs/lucene104/TestFloat16DequantizedViewOverflow.java) (JUnit test)

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
     execute --select-class org.apache.lucene.codecs.lucene104.TestFloat16DequantizedViewOverflow
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/osq-quantization-new/](../../models/osq-quantization-new/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
