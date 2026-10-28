# SlowCodecReaderWrapper's KnnVectorsReader.getVectorCount (new in 10.6.0) returns the vector count of the SegmentReader under every FilterLeafReader/FilterCodecReader layer instead of the wrapped view's, so it disagrees with get*VectorValues(field).size of the same reader (6 instead of 3 for a view that exposes 3 of 6 vectors), and it throws NullPointerException for a vector field that the view adds

Bug LFV-911 · Area: core / index: SlowCodecReaderWrapper vector reader metadata (KnnVectorsReader#getVectorCount, new in 10.6.0) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You wrap a FilterLeafReader that exposes only some of a segment's vectors (in the repro the 3 even documents of 6) with SlowCodecReaderWrapper.wrap, as callers of IndexWriter.addIndexes do, and ask the resulting reader's KnnVectorsReader for getVectorCount(fieldInfo); you expect 3, because the javadoc says it 'has the same meaning as get*VectorValues(field).size' and the same reader's getFloatVectorValues and getByteVectorValues return 3 vectors. Instead it returns 6, the stored count of the SegmentReader under the filter, because the wrapper strips every FilterLeafReader and FilterCodecReader layer before it asks; a custom vectors format sees the same 6 for the 3 vectors it merges during addIndexes. For a view that adds a vector field to a segment without vectors, the call throws NullPointerException instead of returning the count (the javadoc documents only IllegalArgumentException).

## Expected vs actual

- Expected: You expect 3, because the javadoc says it 'has the same meaning as get*VectorValues(field).size' and the same reader's getFloatVectorValues and getByteVectorValues return 3 vectors.
- Actual: It returns 6, the stored count of the SegmentReader under the filter, because the wrapper strips every FilterLeafReader and FilterCodecReader layer before it asks; a custom vectors format sees the same 6 for the 3 vectors it merges during addIndexes.

## Files

- [src/org/apache/lucene/index/TestSlowCodecReaderWrapperVectorCount.java](src/org/apache/lucene/index/TestSlowCodecReaderWrapperVectorCount.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestSlowCodecReaderWrapperVectorCount
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/slow-codec-reader-vector-metadata-answered-past-wrappers/](../../models/slow-codec-reader-vector-metadata-answered-past-wrappers/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
