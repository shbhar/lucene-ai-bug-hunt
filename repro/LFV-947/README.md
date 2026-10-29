# CompositeVerifyQuery's Weight reports itself cacheable from its shape predicate alone and ignores the index query it runs, so after an in-place doc-values update a query cache keeps returning the old matches

Bug LFV-947 · Area: spatial-extras / composite: CompositeVerifyQuery (Weight#isCacheable) and the query cache · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-947.html](../../bugs/LFV-947.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You wrap an index query that reads a doc-values field, for example NumericDocValuesField.newSlowExactQuery("v", 1), in new CompositeVerifyQuery(indexQuery, shapePredicate), run it through a query cache, then change that field for one document in place with IndexWriter#updateNumericDocValue and reopen the reader. You expect the reopened searcher to see the new value, because a query that depends on updated doc values must report itself not cacheable (SegmentCacheable), and the index query alone does. Instead CompositeVerifyQuery's Weight#isCacheable asks only the shape predicate, the segment keeps its core cache key, and the cache keeps serving the pre-update result ([0, 1, 2] instead of [1, 2] in the repro; 5025 instead of 5024 hits with the release-default cache settings).

## Expected vs actual

- Expected: After the update and reopen, the cached composite returns what an uncached search returns: [1, 2, 4] for the shops, [1, 2] for the bare doc-values query, 5024 hits; its Weight reports not cacheable.
- Actual: Through the cache it still returns the pre-update result: [0, 1, 2], [0, 1, 2] and 5025 hits; its Weight reports cacheable.

## Files

- [src/org/apache/lucene/spatial/composite/TestCompositeVerifyQueryCacheability.java](src/org/apache/lucene/spatial/composite/TestCompositeVerifyQueryCacheability.java) (JUnit test)
- [src/DefaultSearcherStaleCount.java](src/DefaultSearcherStaleCount.java) (main() class)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual); a main() class that exits non-zero, with an exception or AssertionError, is the bug reproducing.
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/spatial-extras/build/libs/lucene-spatial-extras-$V.jar
CP=$CP:$LUCENE/lucene/spatial3d/build/libs/lucene-spatial3d-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.spatial.composite.TestCompositeVerifyQueryCacheability

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" DefaultSearcherStaleCount
```

The module(s) spatial-extras also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestCompositeVerifyQueryCacheability.java](TestCompositeVerifyQueryCacheability.java)
- [DefaultSearcherStaleCount.java](DefaultSearcherStaleCount.java)
- [3-TestCompositeVerifyQueryCacheability.java](3-TestCompositeVerifyQueryCacheability.java)
- [TestCompositeVerifyQueryCache.java](TestCompositeVerifyQueryCache.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
