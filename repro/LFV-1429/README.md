# RescoreTopNQuery's full-precision rescoring (float32 and float16) throws UnsupportedOperationException when its inner query matches a document in a segment that still lists the vector field but holds no vectors (its vector documents deleted and merged away): since #16705 the shared value source always takes a copy() of the field's vector values for prefetching, and the codec's empty vector values throw from copy(); 10.6.0 returns the document with score 0 (unreleased: main and branch_11x)

Finding LFV-1429 · Area: search / full-precision vector rescoring (RescoreTopNQuery.createFullPrecisionRescorerQuery, AbstractFullPrecisionVectorSimilarityValuesSource, FullPrecision{Float,Float16}VectorSimilarityValuesSource) x codecs / empty off-heap vector values (lucene95 raw views, lucene104 scalar-quantized views) · Priority: low · Impact: crash · Novelty: new

Finding page: [../../bugs/LFV-1429.html](../../bugs/LFV-1429.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index two documents, only the first with a KnnFloatVectorField "v", delete the first and forceMerge(1), so the one segment still lists "v" but holds no vectors, and you search RescoreTopNQuery.createFullPrecisionRescorerQuery(new TermQuery(id:1), {1, 0}, "v", 1). You expect the matching document back with score 0, which is what RescoreTopNQuery gives a match without a vector and what 10.6.0 returns. Instead IndexSearcher.search throws UnsupportedOperationException: since #16705 AbstractFullPrecisionVectorSimilarityValuesSource.getValues always calls vectorValues.copy() to get a separate prefetch view, and the default codec's EmptyOffHeapVectorValues (what LeafReader.getFloatVectorValues returns for such a segment) throws from copy().

## Expected vs actual

- Expected: 1 hit, the document without a vector, with score 0 (what 10.6.0 returns).
- Actual: `IndexSearcher.search` throws `UnsupportedOperationException` from `EmptyOffHeapVectorValues.copy()` (`OffHeapFloatVectorValues.java:314`), with assertions on and off.

## Files

- [src/org/apache/lucene/search/TestRescoreTopNQueryEmptyVectorSegment.java](src/org/apache/lucene/search/TestRescoreTopNQueryEmptyVectorSegment.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): reproduced.
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
java -da -dsa -Dtests.asserts=false -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestRescoreTopNQueryEmptyVectorSegment
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestRescoreTopNQueryEmptyVectorSegment.java](TestRescoreTopNQueryEmptyVectorSegment.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
