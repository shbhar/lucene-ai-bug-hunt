# LongRange/DoubleRange#getQuery(fastMatchQuery, valueSource) reports its Weight as cacheable without asking the fast-match query, so after an in-place doc-values update the query cache keeps returning the old matches

Bug LFV-320 · Area: facet / range drill-down queries (LongRange and DoubleRange ValueSourceQuery and MultiValueSourceQuery) and the query cache · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-320.html](../../bugs/LFV-320.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a range filter or drill-down with LongRange or DoubleRange#getQuery(fastMatchQuery, valueSource) whose fast-match query reads a doc-values field (for example NumericDocValuesField.newSlowExactQuery("inStock", 1)), run it through a query cache, then change that field for one document in place with IndexWriter#updateNumericDocValue and reopen the reader. You expect the reopened searcher to use the new value, because a query that depends on updated doc values must report itself not cacheable (SegmentCacheable), and the fast-match query alone does. Instead the range query's Weight#isCacheable asks only the value source, the segment keeps its core cache key, and the cache keeps serving the pre-update result (5 hits instead of 4 in the repro) until the segment is merged away or the entry is evicted.

The test shows that a `LongRange#getQuery(fastMatch, valueSource)` query whose fast match reads doc values (`inStock`) keeps its query-cache entry after `updateNumericDocValue` changes `inStock` and the reader is reopened.

## Expected vs actual

Expected: 0 hits (the only document is no longer in stock). Actual (-ea and -da): 1 hit, the cached pre-update result, because the range Weight's `isCacheable` ignores the fast-match Weight.

## Files

- [src/org/apache/lucene/facet/range/TestRangeFastMatchQueryCache.java](src/org/apache/lucene/facet/range/TestRangeFastMatchQueryCache.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.facet.range.TestRangeFastMatchQueryCache
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/query-cache-correctness/](../../models/query-cache-correctness/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
