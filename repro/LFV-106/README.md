# Vector similarity queries report isCacheable == true although their per-segment result depends on live docs, the search timeout and the filter, so a shared query cache serves one reader's or one search's result to another

Bug LFV-106 · Area: search / vector similarity queries and the query cache · Priority: high · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-106.html](../../bugs/LFV-106.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You search with a vector similarity (threshold) query, for example FloatVectorSimilarityQuery, in a non-scoring context such as IndexSearcher#count or ConstantScoreQuery, with a query cache that several readers or searchers share (the IndexSearcher default in 9.10.0-10.5.2). You expect every reader and every search to get the hits it gets without the cache. Instead the cache hands one reader's or one search's per-segment result to another: an older point-in-time reader misses a document that only a newer reader has deleted (99 hits instead of 100), a search without a timeout gets the empty result of an earlier timed-out search (0 instead of 100), and with a filter on an updated doc-values field a newer reader gets a document that no longer matches the filter (100 instead of 99).

The test shows that `FloatVectorSimilarityQuery` says its per-segment result is cacheable, but the result depends on live docs, so a shared query cache gives an older reader the result of a newer reader that deleted a document (same segment core).

## Expected vs actual

Expected `count` 2 on the older reader, where doc 0 is still live; actual 1 (`expected:<2> but was:<1>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/search/TestVectorSimilarityQueryCache.java](src/org/apache/lucene/search/TestVectorSimilarityQueryCache.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.TestVectorSimilarityQueryCache
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/knn-query-layer/](../../models/knn-query-layer/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
