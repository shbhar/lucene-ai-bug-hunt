# SpatialQuery's dense scorers decide 'no match' from a counter summed from bulk-iterator DocIdSetIterator#cost(), so LatLonPoint geometry queries and LatLonShape/XYShape queries with WITHIN or DISJOINT silently drop matching documents when a custom PointValues hands the bulk IntersectVisitor callbacks iterators with an upper-bound cost (latent: every in-tree PointValues reports exact costs; since 9.1.0)

Finding LFV-1377 · Area: document / spatial queries (SpatialQuery.RelationScorerSupplier dense scorers, used by LatLonPoint.newGeometryQuery/newPolygonQuery and LatLonShape/XYShape queries) over a custom PointValues · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1377.html](../../bugs/LFV-1377.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

A PointValues wrapper delegates everything to the default codec's points but hands each visited document to IntersectVisitor#visit(DocIdSetIterator, byte[]) as a one-document BitSetIterator whose cost() is maxDoc, an upper bound that the DocIdSetIterator#cost() javadoc explicitly allows ('generally an upper bound ... may be ... completely inaccurate'). Two documents, one with a single LatLonPoint inside a square and one with two LatLonPoint values outside it: LatLonPoint.newGeometryQuery(WITHIN, square) should count 1, as it does with the exact cost 1. Instead SpatialQuery's dense WITHIN scorer starts a counter at size() = 3 points, subtracts the reported cost 2 for each of the outside document's two points (3 -&gt; 1 -&gt; 0), reaches 0 and returns DocIdSetIterator.empty() although the inside document's bit is still set, so the query counts 0 with -da and trips the internal assert 'cost[0] &gt; 0 || result.cardinality() == 0' (SpatialQuery.java:362) with -ea.

## Expected vs actual

- Expected: The count is 1: doc 0's only point, (1, 1), is inside the square.
- Actual: `AssertionError: expected:<1> but was:<0>` with assertions off; with them on, `AssertionError` at `SpatialQuery.java:362`.

## Files

- [src/org/apache/lucene/document/TestSpatialQueryUpperBoundIteratorCost.java](src/org/apache/lucene/document/TestSpatialQueryUpperBoundIteratorCost.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.document.TestSpatialQueryUpperBoundIteratorCost
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestSpatialQueryUpperBoundIteratorCost.java](TestSpatialQueryUpperBoundIteratorCost.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
