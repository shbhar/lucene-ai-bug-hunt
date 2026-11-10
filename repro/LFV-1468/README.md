# Sandbox CoveringQuery's Weight#isCacheable asks only its minimumNumberMatch source and ignores its sub-weights, so a query cache keeps serving hits computed before an in-place doc-values update of a field one of its clauses reads (since 7.2.0)

Finding LFV-1468 · Area: sandbox / search: CoveringQuery (CoveringWeight#isCacheable) and the query cache · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build new CoveringQuery(List.of(NumericDocValuesField.newSlowExactQuery("x", 1)), LongValuesSource.fromLongField("min")), count it through a query cache, then change x for one document in place with IndexWriter#updateNumericDocValue and reopen the reader. You expect the reopened searcher to see the new value, because a query whose result depends on updated doc values must report itself not cacheable (SegmentCacheable), and both the clause's own weight and a BooleanQuery over the same clause do. Instead CoveringWeight#isCacheable asks only the minimumNumberMatch source, the segment keeps its core cache key, and the cache keeps serving the result from before the update (count 2 instead of 1 in the repro; in the opposite direction a document that starts matching is missing).

## Expected vs actual

- Expected: After the update and reopen, the cached count is 0, the same as an uncached search on the new reader.
- Actual: The cached count is still 1, the result stored before the update (`expected:<0> but was:<1>`).

## Files

- [src/org/apache/lucene/sandbox/search/TestCoveringQueryCacheability.java](src/org/apache/lucene/sandbox/search/TestCoveringQueryCacheability.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:$LUCENE/lucene/sandbox/build/libs/lucene-sandbox-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.sandbox.search.TestCoveringQueryCacheability
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
