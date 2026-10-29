# LatLonShape/XYShape newSlowDocValuesBoxQuery reports Weight#isCacheable == true although it reads the shape from binary doc values, so after an in-place doc-values update the query cache keeps returning the old matches

Bug LFV-308 · Area: core / document: shape doc-values queries (BaseShapeDocValuesQuery via SpatialQuery) and the query cache · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-308.html](../../bugs/LFV-308.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You keep a shape in a doc-values-only field (LatLonShape.createDocValueField or XYShape.createDocValueField), run LatLonShape/XYShape.newSlowDocValuesBoxQuery through a query cache, then move one document's shape out of the box with IndexWriter#updateDocValues and reopen the reader. You expect the reopened searcher to stop matching that document, because a query that reads doc values must report itself not cacheable once those doc values have been updated (SegmentCacheable). Instead its Weight#isCacheable stays true (inherited from SpatialQuery#queryIsCacheable, which always returns true), the segment keeps its core cache key, and the cache keeps serving the pre-update result (5 hits instead of 4 in the repro) until the segment is merged away or the entry is evicted.

The test shows that `LatLonShape.newSlowDocValuesBoxQuery` stays cacheable after an in-place doc-values update, so `LRUQueryCache` keeps serving the result from before the update.

## Expected vs actual

Expected count `0` after the only shape is moved out of the box and the reader is reopened; actual `1`, from the cache (`expected:<0> but was:<1>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/document/TestShapeDocValuesQueryCachedAfterUpdate.java](src/org/apache/lucene/document/TestShapeDocValuesQueryCachedAfterUpdate.java) (JUnit test)

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
     execute --select-class org.apache.lucene.document.TestShapeDocValuesQueryCachedAfterUpdate
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/query-cache-correctness/](../../models/query-cache-correctness/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
