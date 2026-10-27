# Geo3dRectangleShape.reset(minX, maxX, minY, maxY) replaces the wrapped spatial3d box but keeps the old minX/maxX/minY/maxY, so with the Geo3d spatial context BBoxStrategy shape values report a stale box for every document and BBoxStrategy overlap-ratio relevance scores are wrong

Bug LFV-675 · Area: spatial-extras / spatial4j bridge to spatial3d (Geo3dRectangleShape.reset), read by BBoxStrategy's value sources (BBoxValueSource: makeShapeValueSource, makeOverlapRatioValueSource) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You use the Geo3d spatial context (Geo3dSpatialContextFactory) with BBoxStrategy and read each document's box with makeShapeValueSource or rank by makeOverlapRatioValueSource(queryBox,...), or you call reset(10, 20, 30, 40) on a Geo3d rectangle yourself. You expect reset to replace the whole rectangle (the spatial4j Rectangle.reset javadoc: 'Resets the state of this shape given the arguments'; spatial4j's own RectangleImpl.reset assigns all four bounds), so getMinX..getMaxY return 10, 20, 30, 40, the shape values return each document's indexed box, and a document box equal to the query scores 1.0, as it does with the default SpatialContext.GEO. Instead Geo3dRectangleShape.reset rebuilds only the wrapped GeoBBox and clears the center and bounding-box caches, never assigning the four fields that getMinX..getMaxY, getWidth, getHeight and getCrossesDateLine read: the rectangle equals rect(10, 20, 30, 40) but reports its previous bounds, BBoxValueSource (which creates one rect(0, 0, 0, 0) per segment and resets it for each document) reports (0, 0, 0, 0) for every document, and the self-overlap score is 0.0.

## Expected vs actual

- Expected: You expect reset to replace the whole rectangle (the spatial4j Rectangle.reset javadoc: 'Resets the state of this shape given the arguments'; spatial4j's own RectangleImpl.reset assigns all four bounds), so getMinX..getMaxY return 10, 20, 30, 40, the shape values return each document's indexed box, and a document box equal to the query scores 1.0, as it does with the default SpatialContext.GEO.
- Actual: Geo3dRectangleShape.reset rebuilds only the wrapped GeoBBox and clears the center and bounding-box caches, never assigning the four fields that getMinX..getMaxY, getWidth, getHeight and getCrossesDateLine read: the rectangle equals rect(10, 20, 30, 40) but reports its previous bounds, BBoxValueSource (which creates one rect(0, 0, 0, 0) per segment and resets it for each document) reports (0, 0, 0, 0) for every document, and the self-overlap score is 0.0.

## Files

- [src/org/apache/lucene/spatial/spatial4j/TestGeo3dRectangleReset.java](src/org/apache/lucene/spatial/spatial4j/TestGeo3dRectangleReset.java) (JUnit test)

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

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.spatial.spatial4j.TestGeo3dRectangleReset
```

The module(s) spatial-extras also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
