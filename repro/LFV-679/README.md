# Geo3dShapeFactory.pointXYZ(x, y, z) and the Geo3d builders' pointXYZ read longitude, latitude and a third value as geocentric Cartesian x, y, z, so 3-coordinate points, lines and multi-points land somewhere else (pointXYZ(30, 10, 0) becomes lon 18.43, lat 0) and polygons with z = 0 are rejected as 'all points are coplanar' (since 7.1.0)

Bug LFV-679 · Area: spatial-extras / spatial4j bridge to spatial3d (Geo3dShapeFactory.pointXYZ and Geo3dPointBuilder.pointXYZ, which the line, polygon, hole and multi-point builders share), reached through the public spatial4j ShapeFactory / PointsBuilder API and spatial4j's GeoJSON reader in the Geo3d spatial context · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You use the Geo3d spatial context and build a point from longitude 30, latitude 10 and an elevation of 0, with ShapeFactory.pointXYZ(30, 10, 0) or by reading the GeoJSON position [30, 10, 0], and expect the point (30, 10), which is what spatial4j's own factory returns and what its contract implies (pointXYZ is pointXY plus a third dimension that an implementation may ignore or reject). Instead you get longitude 18.43, latitude 0: Geo3dShapeFactory.pointXYZ and the builders' shared Geo3dPointBuilder.pointXYZ hand the three numbers to spatial3d's new GeoPoint(x, y, z), which reads them as a geocentric Cartesian vector, so points, lines and multi-points built this way silently land elsewhere (a multi-point relates DISJOINT to its own vertex) and a polygon whose vertices all have z = 0 is rejected with InvalidShapeException 'Invalid polygon, all points are coplanar'.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: You get longitude 18.43, latitude 0: Geo3dShapeFactory.pointXYZ and the builders' shared Geo3dPointBuilder.pointXYZ hand the three numbers to spatial3d's new GeoPoint(x, y, z), which reads them as a geocentric Cartesian vector, so points, lines and multi-points built this way silently land elsewhere (a multi-point relates DISJOINT to its own vertex) and a polygon whose vertices all have z = 0 is rejected with InvalidShapeException 'Invalid polygon, all points are coplanar'.

## Files

- [src/org/apache/lucene/spatial/spatial4j/TestGeo3dPointXYZ.java](src/org/apache/lucene/spatial/spatial4j/TestGeo3dPointXYZ.java) (JUnit test)
- [src2/org/apache/lucene/spatial/spatial4j/TestGeo3dPointXYZ.java](src2/org/apache/lucene/spatial/spatial4j/TestGeo3dPointXYZ.java) (JUnit test)

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
     execute --select-class org.apache.lucene.spatial.spatial4j.TestGeo3dPointXYZ
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.spatial.spatial4j.TestGeo3dPointXYZ
```

The module(s) spatial-extras also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
