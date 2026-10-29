# Geo3dShape.relate(Rectangle) swaps WITHIN and CONTAINS when the rectangle is not a Geo3d shape (such as ctx.getWorldBounds or a PackedQuadPrefixTree cell), so IsWithin/Contains on stored shapes give the opposite answer and RecursivePrefixTreeStrategy over PackedQuadPrefixTree returns documents far outside the query shape (regression in 7.1.0)

Bug LFV-673 · Area: spatial-extras / spatial4j bridge to spatial3d (Geo3dShape.relate(Rectangle) and its shared GeoArea-to-SpatialRelation mapping), reached through SpatialOperation IsWithin/Contains on SerializedDVStrategy and CompositeSpatialStrategy, RecursivePrefixTreeStrategy cell traversal over PackedQuadPrefixTree, and any spatial4j relate against ctx.getWorldBounds · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-673.html](../../bugs/LFV-673.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You use the Geo3d spatial context and relate a Geo3d shape to a spatial4j rectangle that the Geo3d shape factory did not build, for example a 10-degree circle against the context's own ctx.getWorldBounds, or run a SerializedDVStrategy IsWithin(ctx.getWorldBounds) query over that circle, and expect WITHIN (the circle lies inside the world) and one hit. Instead relate returns CONTAINS and the query matches nothing, while Contains(world bounds) matches the circle: Geo3dShape.relate(Rectangle) asks spatial3d how this shape relates to the rectangle but then applies the mapping written for the opposite question (how the other shape relates to this one), so the two nested answers come out swapped, whereas the same box built by the Geo3d factory relates correctly. Lucene builds such rectangles itself, so RecursivePrefixTreeStrategy over PackedQuadPrefixTree, whose cells are plain spatial4j rectangles, treats a cell that contains a small query circle as lying inside the circle and returns every document in that cell, for example a point at (100.3, 37.7) for a half-degree circle at (1, 1).

The test shows that in the Geo3d spatial context `Geo3dShape.relate(Rectangle)` swaps WITHIN and CONTAINS for a rectangle that is not a Geo3d shape, here the context's own `ctx.getWorldBounds()`.

## Expected vs actual

Expected `circle(0, 0, 10).relate(ctx.getWorldBounds())` to be `WITHIN`; actual `CONTAINS` (`expected:<WITHIN> but was:<CONTAINS>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/spatial/spatial4j/TestGeo3dShapeRelateWorldBounds.java](src/org/apache/lucene/spatial/spatial4j/TestGeo3dShapeRelateWorldBounds.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/spatial-extras/build/libs/lucene-spatial-extras-$V.jar
CP=$CP:$LUCENE/lucene/spatial3d/build/libs/lucene-spatial3d-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.spatial.spatial4j.TestGeo3dShapeRelateWorldBounds
```

The module(s) spatial-extras also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
