# Merging into a Lucene104 scalar-quantized FLOAT32 vector field that does not use COSINE centers the merge source's own vector arrays in place, so IndexWriter.addIndexes changes a source that keeps its arrays (MemoryIndex, FloatVectorValues.fromFloats) and can store wrong full-precision vectors or wrong quantized codes

Bug LFV-420 · Area: codecs / quantized vector formats merge (Lucene104 scalar-quantized, Lucene102 binary-quantized) with index / addIndexes through SlowCodecReaderWrapper and memory / MemoryIndex · Priority: medium · Impact: data corruption · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a FLOAT32 field with Lucene104ScalarQuantizedVectorsFormat and EUCLIDEAN, DOT_PRODUCT or MAXIMUM_INNER_PRODUCT by calling IndexWriter.addIndexes(SlowCodecReaderWrapper.wrap(...)) on a MemoryIndex, or on a FilterLeafReader that serves new vectors from your own list through FloatVectorValues.fromFloats, and you expect the merge only to read its source, as it does for COSINE fields and for the older Lucene99 scalar-quantized formats. Instead the quantizer subtracts the merged centroid from the source's own arrays: afterwards the MemoryIndex vector {1,2,3,4} reads back as {0,0,0,0} when it is the only vector, and as {1.5,0.75,-2,1.5} when merged together with a second source holding {-2,0.5,7,1}, a second addIndexes of it stores that centered vector as the full-precision vector, and your list holds centered vectors. When two documents share one float in a single addIndexes, the second is quantized from the already-centered array, so it scores 0.160 against 0.066 for its identical copies (exact 0.066) and ranks first.

## Expected vs actual

- Expected: You expect the merge only to read its source, as it does for COSINE fields and for the older Lucene99 scalar-quantized formats.
- Actual: The quantizer subtracts the merged centroid from the source's own arrays: afterwards the MemoryIndex vector {1,2,3,4} reads back as {0,0,0,0} when it is the only vector, and as {1.5,0.75,-2,1.5} when merged together with a second source holding {-2,0.5,7,1}, a second addIndexes of it stores that centered vector as the full-precision vector, and your list holds centered vectors.

## Files

- [src/org/apache/lucene/index/TestQuantizedMergeSourceVectorsUnchanged.java](src/org/apache/lucene/index/TestQuantizedMergeSourceVectorsUnchanged.java) (JUnit test)
- [src/org/apache/lucene/index/TestHistoryBq102SourceUnchanged.java](src/org/apache/lucene/index/TestHistoryBq102SourceUnchanged.java) (JUnit test)
- [src/org/apache/lucene/index/TestHistorySq99SourceUnchanged.java](src/org/apache/lucene/index/TestHistorySq99SourceUnchanged.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestQuantizedMergeSourceVectorsUnchanged

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestHistoryBq102SourceUnchanged \
       --select-class org.apache.lucene.index.TestHistorySq99SourceUnchanged
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
