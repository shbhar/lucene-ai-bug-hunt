# Spatial heatmap: HeatmapFacetCounter gives non-zero counts to cells outside a non-rectangular input shape when documents are indexed with coarse leaf cells, although the javadoc says those cells are always 0

Bug LFV-670 · Area: spatial-extras / prefix-tree heatmap faceting (HeatmapFacetCounter.calcFacets, also reached through PrefixTreeStrategy.calcFacets; coarse 'ancestor' leaf cells reported by PrefixTreeFacetCounter) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-670.html](../../bugs/LFV-670.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index non-point shapes (rectangles, polygons) with the default RecursivePrefixTreeStrategy and ask PrefixTreeStrategy.calcFacets (HeatmapFacetCounter) for a heatmap with a circle or another non-rectangular input shape; the javadoc says the grid covers the input's bounding rectangle and that 'the non-intersecting cells will all be 0'. Instead, a document whose shape was indexed as a leaf cell coarser than the heatmap level has its count added to every heatmap cell under that leaf, including cells that do not intersect the input shape at all, because the code that spreads these coarse ('ancestor') counts never checks the cells against the input shape; point documents and rectangular inputs come out right. In the minimal repro (the setup of Lucene's own testQueryCircle plus one square document), 8 cells that are disjoint from the circle report 1 instead of 0, with no error.

The test shows that `PrefixTreeStrategy.calcFacets` with a circle input gives a non-zero count to a heatmap cell outside the circle when the document (the square [45,90]x[45,90]) is indexed as a leaf cell coarser than facetLevel.

## Expected vs actual

Expected 0 for the top-right cell [78.75,90]x[78.75,90], which does not intersect circle(0,0,89) (javadoc: "the non-intersecting cells will all be 0"); actual `expected:<0> but was:<1>` (-ea and -da).

## Files

- [src/org/apache/lucene/spatial/prefix/TestHeatmapAncestorCellsOutsideInputShape.java](src/org/apache/lucene/spatial/prefix/TestHeatmapAncestorCellsOutsideInputShape.java) (JUnit test)

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
     execute --select-class org.apache.lucene.spatial.prefix.TestHeatmapAncestorCellsOutsideInputShape
```

The module(s) spatial-extras also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
