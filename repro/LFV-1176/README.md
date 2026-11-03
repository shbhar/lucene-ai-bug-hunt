# With the flat Lucene104ScalarQuantizedVectorsFormat, a FloatVectorSimilarityQuery (or the 4-argument LeafReader.searchNearestVectors) on a segment whose float vector field has no vectors left (every vector document deleted and merged away) throws NullPointerException ('quantizer' is null) instead of returning no hits (10.4.0 through 10.6.0 and main)

Finding LFV-1176 · Area: codecs / Lucene104 scalar-quantized vectors (flat format search on a field with no vectors: Lucene104ScalarQuantizedVectorsReader.search) · Priority: low · Impact: crash · Novelty: new

Finding page: [../../bugs/LFV-1176.html](../../bugs/LFV-1176.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index float vectors with the flat Lucene104ScalarQuantizedVectorsFormat, delete every document that has a vector while other documents remain, and merge, so the merged segment still lists the vector field but holds no vectors (the same index Lucene's own test testDeleteAllVectorDocs builds). You expect a FloatVectorSimilarityQuery on that index to return no hits, as it does with the HNSW formats and as KnnFloatVectorQuery does on the same index. Instead IndexSearcher.search throws NullPointerException ('quantizer' is null), because the reader's search() returns early only when k is 0 and then builds a scorer over the empty vector values, whose quantizer is null.

## Expected vs actual

- Expected: `searcher.count(...)` returns 0.
- Actual: `NullPointerException: ... because "quantizer" is null` at `Lucene104ScalarQuantizedVectorScorer.getRandomQuantizedVectorScorer(Lucene104ScalarQuantizedVectorScorer.java:119)`, assertions on and off.

## Files

- [src/org/apache/lucene/codecs/lucene104/TestFlatScalarQuantizedEmptyFieldSearch.java](src/org/apache/lucene/codecs/lucene104/TestFlatScalarQuantizedEmptyFieldSearch.java) (JUnit test)

## Run it

- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.codecs.lucene104.TestFlatScalarQuantizedEmptyFieldSearch
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestFlatScalarQuantizedEmptyFieldSearch.java](TestFlatScalarQuantizedEmptyFieldSearch.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
