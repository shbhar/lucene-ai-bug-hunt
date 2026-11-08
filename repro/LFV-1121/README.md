# With the Panama vectorization provider, a KnnFloatVectorQuery shorter than the field dimension returns hits on an mmap-backed Lucene99 scalar-quantized vector field instead of throwing IllegalArgumentException, because the mmap branch of Lucene99MemorySegmentScalarQuantizedVectorScorer.getRandomVectorScorer(float[]) skips FlatVectorsScorer.checkDimensions (regression in 10.4.0)

Finding LFV-1121 · Area: codecs / vectorization (Panama scorer for the legacy Lucene99 scalar-quantized vectors format, read through backward-codecs) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1121.html](../../bugs/LFV-1121.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You open a Lucene99 scalar-quantized vector field of dimension 8 (EUCLIDEAN, 7 bits, 50 docs) in an MMapDirectory with the Panama vectorization provider enabled and search it with a KnnFloatVectorQuery whose query vector has 7 components. You expect IllegalArgumentException 'vector query dimension: 7 differs from field dimension: 8', which Lucene's kNN query tests require and which the same index gives with the default provider, with a non-mmap directory and on 10.3.2. Instead, with assertions disabled, the search returns 10 hits and no error: the mmap branch of Lucene99MemorySegmentScalarQuantizedVectorScorer.getRandomVectorScorer(float[]) never calls FlatVectorsScorer.checkDimensions, quantizes the query at its own length, and scores each document on its first 7 codes with Panama kernels that check lengths only with assert.

## Expected vs actual

- Expected: `IllegalArgumentException` "vector query dimension: 7 differs from field dimension: 8" (`FlatVectorsScorer.checkDimensions`; Lucene's kNN tests pin this exception and message form).
- Actual: Assertions off: "Expected exception IllegalArgumentException but no exception was thrown" (4 hits); on: `AssertionError` at `PanamaVectorUtilSupport.uint8SquareDistanceBody:997`.

## Files

- [src/org/apache/lucene/backward_codecs/lucene99/TestLucene99MmapScalarQuantizedQueryDimension.java](src/org/apache/lucene/backward_codecs/lucene99/TestLucene99MmapScalarQuantizedQueryDimension.java) (JUnit test)

## Run it

- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced, with assertions on and off (the Panama vector code on).
- Helpers: the test uses three unchanged classes of Lucene's own backward-codecs test sources that the release jars do not contain (Lucene99RWV1HnswScalarQuantizationVectorsFormat, Lucene99RWScalarQuantizedVectorsFormat and Lucene99ScalarQuantizedVectorsWriter), so the recipe first copies them from lucene/backward-codecs/src/test/org/apache/lucene/backward_codecs/lucene99/ of the checkout into src/ here, where they compile with the test.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/backward-codecs/build/libs/lucene-backward-codecs-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

# helpers: three unchanged classes of Lucene's own backward-codecs test sources
for c in Lucene99RWV1HnswScalarQuantizationVectorsFormat Lucene99RWScalarQuantizedVectorsFormat Lucene99ScalarQuantizedVectorsWriter; do cp $LUCENE/lucene/backward-codecs/src/test/org/apache/lucene/backward_codecs/lucene99/$c.java src/org/apache/lucene/backward_codecs/lucene99/; done

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=3 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.backward_codecs.lucene99.TestLucene99MmapScalarQuantizedQueryDimension
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestLucene99MmapScalarQuantizedQueryDimension.java](TestLucene99MmapScalarQuantizedQueryDimension.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
