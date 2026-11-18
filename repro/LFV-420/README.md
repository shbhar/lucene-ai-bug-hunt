# Merging into a Lucene104 scalar-quantized FLOAT32 vector field that does not use COSINE centers the merge source's own vector arrays in place, so IndexWriter.addIndexes changes a source that keeps its arrays (MemoryIndex, FloatVectorValues.fromFloats) and can store wrong full-precision vectors or wrong quantized codes

Finding LFV-420 · Area: codecs / quantized vector formats merge (Lucene104 scalar-quantized, Lucene102 binary-quantized) with index / addIndexes through SlowCodecReaderWrapper and memory / MemoryIndex · Priority: medium · Impact: data corruption · Novelty: known outside Lucene

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a FLOAT32 field with Lucene104ScalarQuantizedVectorsFormat and EUCLIDEAN, DOT_PRODUCT or MAXIMUM_INNER_PRODUCT by calling IndexWriter.addIndexes(SlowCodecReaderWrapper.wrap(...)) on a MemoryIndex, or on a FilterLeafReader that serves new vectors from your own list through FloatVectorValues.fromFloats, and you expect the merge only to read its source, as it does for COSINE fields and for the older Lucene99 scalar-quantized formats. Instead the quantizer subtracts the merged centroid from the source's own arrays: afterwards the MemoryIndex vector {1,2,3,4} reads back as {0,0,0,0} when it is the only vector, and as {1.5,0.75,-2,1.5} when merged together with a second source holding {-2,0.5,7,1}, a second addIndexes of it stores that centered vector as the full-precision vector, and your list holds centered vectors. When two documents share one float[] in a single addIndexes, the second is quantized from the already-centered array, so it scores 0.160 against 0.066 for its identical copies (exact 0.066) and ranks first.

## Expected vs actual

- Expected: You expect the merge only to read its source, as it does for COSINE fields and for the older Lucene99 scalar-quantized formats.
- Actual: The quantizer subtracts the merged centroid from the source's own arrays: afterwards the MemoryIndex vector {1,2,3,4} reads back as {0,0,0,0} when it is the only vector, and as {1.5,0.75,-2,1.5} when merged together with a second source holding {-2,0.5,7,1}, a second addIndexes of it stores that centered vector as the full-precision vector, and your list holds centered vectors.

## Files

- [src/org/apache/lucene/index/TestQuantizedMergeSourceVectorsUnchanged.java](src/org/apache/lucene/index/TestQuantizedMergeSourceVectorsUnchanged.java) (JUnit test)
- [src/org/apache/lucene/index/TestHistoryBq102SourceUnchanged.java](src/org/apache/lucene/index/TestHistoryBq102SourceUnchanged.java) (JUnit test)
- [src/org/apache/lucene/index/TestHistorySq99SourceUnchanged.java](src/org/apache/lucene/index/TestHistorySq99SourceUnchanged.java) (JUnit test)

## Run it

- Tested on Lucene main 1cfa86310f76 (2026-11-17): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- The three tests are for different Lucene versions, and no version compiles all three: TestQuantizedMergeSourceVectorsUnchanged uses the Lucene104 scalar-quantized formats of 10.4.0 and later, and the two history tests import the Lucene102 binary-quantized and the Lucene99 scalar-quantized HNSW format from lucene-core, which had them up to 10.3.2 (since 10.4.0 they are in the backward-codecs jar under other package names). So the javac line below, which compiles every file under src, fails as printed, and both runs then find no test (run on 2026-11-18 on main 1cfa86310f76, on branch_11x 89e68e0f3a50 and on the 10.6.0 release jars: 4 compile errors, 0 tests found). Give javac only the file or files for your version in place of $(find src -name '*.java'). With TestQuantizedMergeSourceVectorsUnchanged.java alone, run 1 fails 4 of 4 on main 1cfa86310f76, on branch_11x 89e68e0f3a50 (V=11.0.0-SNAPSHOT) and on the 10.6.0 release jars (V=10.6.0, the jars placed where the CP lines look for them), each with assertions on and off, and on 10.5.2 and 10.4.0 with assertions on; run 2 finds no class there. Its four tests show the vector {1,2,3,4} of a MemoryIndex source changed by one addIndexes and stored changed by a second one ('expected:&lt;1.0&gt; but was:&lt;0.0&gt;'), a caller's list behind FloatVectorValues.fromFloats changed ('expected:&lt;1.0&gt; but was:&lt;-1.5&gt;'), and two identical vectors, from one reader passed twice to one addIndexes, scored 0.1377274 and 0.04494382; the vector {1.5,0.75,-2,1.5} and the scores 0.160 and 0.066 in the text above come from another test class of this finding, which is not in this folder. With the two history tests alone, on the 10.3.2 release jars (assertions on and off) and on 10.2.2 and 10.2.0 (assertions on), run 2 finds 2 tests and 1 fails: TestHistoryBq102SourceUnchanged fails with 'expected:&lt;1.0&gt; but was:&lt;0.0&gt;' (the Lucene102 binary-quantized format changed the source's vector), and TestHistorySq99SourceUnchanged passes, as a control (the older Lucene99 scalar-quantized format leaves the source unchanged); run 1 finds no class there.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
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

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
