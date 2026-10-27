# S2PrefixTree: a Contains query whose shape is a point P misses a document indexed with that same point P when P lies on an S2 cell edge (the equator, an S2 face edge, (0,0)), because the query side follows every child cell that touches P while indexing puts P in one cell per level

Bug LFV-371 · Area: spatial-extras / prefix trees (S2PrefixTree and S2PrefixTreeCell under RecursivePrefixTreeStrategy; Contains through ContainsPrefixTreeQuery) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a document with point P through RecursivePrefixTreeStrategy over S2PrefixTree in the Geo3d spatial context and search with SpatialOperation.Contains and the same point P, expecting the document, because Contains is documented as the OGC 'Covers' predicate and its evaluate(P, P) is true, RecursivePrefixTreeStrategy builds a ContainsPrefixTreeQuery for it, a point query is resolved at full precision, and the geohash and quad trees do return the document. When P lies exactly on an S2 cell edge, for example Point(10, 0) on the equator, Contains finds 0 documents while Intersects finds 1. Indexing puts P in one cell per level (S2PrefixTree.getTreeCellIterator, from S2CellId.fromLatLng), but at query time S2PrefixTreeCell.getNextLevelCells has no point case and returns every child cell that touches P (2 on an edge, 4 at (0,0)), and ContainsPrefixTreeQuery requires the document to be in all of them.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/spatial/prefix/tree/TestS2PointOnCellEdgeContains.java](src/org/apache/lucene/spatial/prefix/tree/TestS2PointOnCellEdgeContains.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/spatial-extras/build/libs/lucene-spatial-extras-$V.jar
CP=$CP:$LUCENE/lucene/spatial3d/build/libs/lucene-spatial3d-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.spatial.prefix.tree.TestS2PointOnCellEdgeContains
```

The module(s) spatial-extras also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
