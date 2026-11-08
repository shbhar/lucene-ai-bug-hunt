# A KnnFloatVectorQuery of the wrong length on a Lucene99 scalar-quantized (int7/int4) vector field fails with the query and field dimensions swapped in its message ('vector query dimension: 4 differs from field dimension: 3' for a 3-component query on a 4-dimension field), because Lucene99ScalarQuantizedVectorScorer calls FlatVectorsScorer.checkDimensions(queryLen, fieldLen) with its two arguments reversed (introduced in 10.4.0)

Finding LFV-1230 · Area: codecs / vectors (heap scorer of the legacy Lucene99 scalar-quantized vectors format, read through backward-codecs) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1230.html](../../bugs/LFV-1230.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You open a field written with the legacy Lucene99 scalar-quantized HNSW format (dimension 4) and search it with a KnnFloatVectorQuery whose vector has 3 components. You expect IllegalArgumentException 'vector query dimension: 3 differs from field dimension: 4', the text FlatVectorsScorer.checkDimensions(queryLen, fieldLen) builds and that Lucene's kNN query tests require for other formats. Instead the message reads 'vector query dimension: 4 differs from field dimension: 3', because Lucene99ScalarQuantizedVectorScorer.fromVectorSimilarity calls checkDimensions(values.dimension(), targetBytes.length) with the two arguments reversed; the exception type and the rejection itself are correct.

## Expected vs actual

- Expected: `IllegalArgumentException: vector query dimension: 1 differs from field dimension: 2`.
- Actual: `vector query dimension: 2 differs from field dimension: 1`, with assertions on and off.

## Files

- [src/org/apache/lucene/backward_codecs/lucene99/TestLucene99SQQueryDimensionMessage.java](src/org/apache/lucene/backward_codecs/lucene99/TestLucene99SQQueryDimensionMessage.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
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
     execute --select-class org.apache.lucene.backward_codecs.lucene99.TestLucene99SQQueryDimensionMessage
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestLucene99SQQueryDimensionMessage.java](TestLucene99SQQueryDimensionMessage.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
