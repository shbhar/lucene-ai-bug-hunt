# HeatmapFacetCounter.calcFacets throws InvalidShapeException for a box that ends at the world's east or north edge in a non-geo context with projected (Web Mercator) world bounds, because floating-point rounding adds one cell past that edge

Bug LFV-666 · Area: spatial-extras / prefix-tree heatmap faceting (HeatmapFacetCounter.calcFacets, reached through PrefixTreeStrategy.calcFacets) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You configure a non-geo SpatialContext (geo=false) with Web Mercator world bounds such as ENVELOPE(-20037508.34, 20037508.34, 20037508.34, -20037508.34), index points with RecursivePrefixTreeStrategy over the default 12-level QuadPrefixTree, and ask PrefixTreeStrategy.calcFacets for the heatmap of a box inside the world that ends at its east edge, for example x in [3,000 km, east edge], y in [0, 1,000 km] at facet level 5. You expect a heatmap whose region covers the box (14 columns by 1 row), as the calcFacets javadoc describes, or at worst the documented too-many-cells IllegalArgumentException. Instead calcRowsOrCols computes range / cellWidth = 14.000000000000002 in floating point, Math.ceil makes that 15 columns, the region would end a whole cell past the east edge, the non-geo code takes the branch commented 'wraps dateline (won't happen if !geo)' and makes maxX negative, and building the region throws InvalidShapeException 'maxX must be &gt;= minX'; the same box at the north edge throws 'Y values [...] not in boundary'.

The test shows that in a non-geo SpatialContext with the world ENVELOPE(-20037508.34, 20037508.34, 20037508.34, -20037508.34), PrefixTreeStrategy.calcFacets at level 5 for the box x [3,000 km, east edge], y [0, 1,000 km] throws instead of returning a heatmap (TestHeatmapBoxAtWorldEastEdgeNonGeo#testBoxEndingAtWorldEastEdge).

## Expected vs actual

Expected: a heatmap whose region covers the box (14 x 1 cells); actual: `InvalidShapeException: maxX must be >= minX: 2504688.5425 to -1.878516406875E7` at HeatmapFacetCounter.calcFacets:172, with -ea and -da.

## Files

- [src/org/apache/lucene/spatial/prefix/TestHeatmapBoxAtWorldEastEdgeNonGeo.java](src/org/apache/lucene/spatial/prefix/TestHeatmapBoxAtWorldEastEdgeNonGeo.java) (JUnit test)

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
     execute --select-class org.apache.lucene.spatial.prefix.TestHeatmapBoxAtWorldEastEdgeNonGeo
```

The module(s) spatial-extras also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
