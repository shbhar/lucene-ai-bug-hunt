# The flat scalar-quantized vectors format (Lucene104ScalarQuantizedVectorsFormat) accepts BYTE vector fields but its byte search is a no-op, so unfiltered byte kNN and byte similarity queries return no hits

Bug LFV-468 · Area: codecs / vectors (flat scalar-quantized format: byte kNN and byte similarity search) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a BYTE vector field with Lucene104ScalarQuantizedVectorsFormat, a registered stand-alone flat (brute-force) format that accepts BYTE fields without error and searches its FLOAT32 and FLOAT16 fields by scoring every vector, and expect a byte kNN query to return the k nearest documents. Instead the reader hands byte searches to the raw flat reader, whose search method does nothing, so an unfiltered KnnByteVectorQuery returns 0 hits and ByteVectorSimilarityQuery.Adaptive with threshold 0 matches 0 of 10 documents, with no error. The same kNN query with a term filter that matches every document returns the exact top 3, because the filtered path falls back to exact search when the reader returns too few hits.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The reader hands byte searches to the raw flat reader, whose search method does nothing, so an unfiltered KnnByteVectorQuery returns 0 hits and ByteVectorSimilarityQuery.Adaptive with threshold 0 matches 0 of 10 documents, with no error.

## Files

- [src/org/apache/lucene/search/TestFlatQuantizedFormatByteSearch.java](src/org/apache/lucene/search/TestFlatQuantizedFormatByteSearch.java) (JUnit test)
- [src2/org/apache/lucene/search/TestFlatQuantizedFormatByteSearch.java](src2/org/apache/lucene/search/TestFlatQuantizedFormatByteSearch.java) (JUnit test)
- [src3/org/apache/lucene/search/TestFlatQuantizedFormatByteSearch.java](src3/org/apache/lucene/search/TestFlatQuantizedFormatByteSearch.java) (JUnit test)

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
javac -d classes-src3 -cp "$CP" $(find src3 -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestFlatQuantizedFormatByteSearch
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestFlatQuantizedFormatByteSearch
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src3:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestFlatQuantizedFormatByteSearch
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
