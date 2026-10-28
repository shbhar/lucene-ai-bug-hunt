# PackedQuadPrefixTree in a geo context indexes a point at longitude +180 under the -180 edge cell, but point queries look in the +180 edge cell, so RPT and CompositeSpatialStrategy Intersects/Contains with that same point miss the document

Bug LFV-705 · Area: spatial-extras / prefix trees: PackedQuadPrefixTree point-to-cell lookup (RecursivePrefixTreeStrategy and CompositeSpatialStrategy point queries) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-705.html](../../bugs/LFV-705.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a point at longitude +180, for example Pt(180, 10), with RecursivePrefixTreeStrategy over PackedQuadPrefixTree(SpatialContext.GEO, 8) and query Intersects or Contains with that same point; the document must match, since a point intersects and contains itself and the prefix-tree approximation may only add matches (SpatialArgs: the error 'should not shrink' the shape). Instead the query returns 0 hits, and so does CompositeSpatialStrategy's Intersects: indexing keeps the first cell in Z order whose rectangle relates to the point, and a geo rectangle treats +180 as -180, so the point is stored in the cells at the -180 edge, while the point query's cell lookup (PackedQuadPrefixTree.getCell, a mid-point walk since LUCENE-8755 in 8.3.0) sends x=+180 to the cells at the +180 edge. The same point at -180, QuadPrefixTree, and a rectangle query touching +180 still find the document.

## Expected vs actual

- Expected: Each of the three queries for `Pt(180, 10)` finds the document at `Pt(180, 10)`: 1 hit, as the three controls do.
- Actual: RPT Intersects, RPT Contains and `CompositeSpatialStrategy` Intersects each return 0 hits (`expected:<1> but was:<0>`); the controls return 1.

## Files

- [src/org/apache/lucene/spatial/prefix/TestPackedQuadPlus180Point.java](src/org/apache/lucene/spatial/prefix/TestPackedQuadPlus180Point.java) (JUnit test)
- [src2/org/apache/lucene/spatial/prefix/TestPackedQuadPlus180Point.java](src2/org/apache/lucene/spatial/prefix/TestPackedQuadPlus180Point.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/spatial-extras/build/libs/lucene-spatial-extras-$V.jar
CP=$CP:$LUCENE/lucene/spatial3d/build/libs/lucene-spatial3d-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.spatial.prefix.TestPackedQuadPlus180Point
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.spatial.prefix.TestPackedQuadPlus180Point
```

The module(s) spatial-extras also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestPackedQuadPlus180Point.java](TestPackedQuadPlus180Point.java)
- [Plus180CellProbe.java](Plus180CellProbe.java)
- [TestPlus180FixNeighbours.java](TestPlus180FixNeighbours.java)
- [TestPlus180CrossSignTable.java](TestPlus180CrossSignTable.java)

Formal models and scripts this bug's part of the hunt used: [../../models/spatial-extras-composite-and-prefix-tree-query-cross-check/](../../models/spatial-extras-composite-and-prefix-tree-query-cross-check/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
