# GeoBBox.expand clamps a widened longitude bound at +-180 degrees instead of wrapping it across the antimeridian, so expanded geo3d boxes (and Geo3dRectangleShape.getBuffered / WKT BUFFER(ENVELOPE(...)) in a Geo3D spatial context) miss the part beyond the dateline

Bug LFV-611 · Area: spatial3d / geom bounding boxes (GeoBBox.expand in the 13 GeoBBox classes with a longitude range, GeoBBoxFactory.makeGeoBBox); spatial-extras / spatial4j Geo3D context (Geo3dRectangleShape.getBuffered, reached by spatial4j's WKT BUFFER) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You take a geo3d box whose east edge is near the dateline, for example longitudes [2.5, 3.0] radians, and call expand(0.3), or in a Geo3D spatial context you search 'Intersects(BUFFER(ENVELOPE(172, 178, 4, -4), 4))'. You expect each bound to move out by the angle, so the box now crosses the dateline and reaches -2.983 radians (-178 degrees), as the same code already does for a box that crosses the dateline and as spatial4j's own geo rectangle buffer does. Instead expand passes the unwrapped bound 3.3 to GeoBBoxFactory.makeGeoBBox, which clamps it to PI, so the box stops at 180 degrees and a document at (-179.5, 1) is not found (0 hits instead of 1); the west edge and all 13 GeoBBox classes with a longitude range behave the same way.

## Expected vs actual

- Expected: You expect each bound to move out by the angle, so the box now crosses the dateline and reaches -2.983 radians (-178 degrees), as the same code already does for a box that crosses the dateline and as spatial4j's own geo rectangle buffer does.
- Actual: Expand passes the unwrapped bound 3.3 to GeoBBoxFactory.makeGeoBBox, which clamps it to PI, so the box stops at 180 degrees and a document at (-179.5, 1) is not found (0 hits instead of 1); the west edge and all 13 GeoBBox classes with a longitude range behave the same way.

## Files

- [src/org/apache/lucene/spatial3d/geom/TestGeoBBoxExpandAcrossDateline.java](src/org/apache/lucene/spatial3d/geom/TestGeoBBoxExpandAcrossDateline.java) (JUnit test)
- [src/org/apache/lucene/spatial/spatial4j/TestGeo3dRectangleBufferedAcrossDateline.java](src/org/apache/lucene/spatial/spatial4j/TestGeo3dRectangleBufferedAcrossDateline.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.spatial3d.geom.TestGeoBBoxExpandAcrossDateline \
       --select-class org.apache.lucene.spatial.spatial4j.TestGeo3dRectangleBufferedAcrossDateline
```

The module(s) spatial-extras also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
