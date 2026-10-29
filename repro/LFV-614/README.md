# GeoDegenerateLatitudeZone.isWithin compares z with sin(latitude) at a 1e-10 tolerance instead of using its own planet-model plane, so a zero-height geo3d box spanning all longitudes rejects the points on its own latitude on WGS84 and, on any planet model, can report CONTAINS for areas just below it, which makes Geo3D prefix-tree searches match points tens of degrees away

Bug LFV-614 · Area: spatial3d / geom bounding boxes (GeoDegenerateLatitudeZone, which GeoBBoxFactory.makeGeoBBox returns for a box with equal top and bottom latitude spanning all longitudes), reached through Geo3DPoint.newBoxQuery / newShapeQuery and the spatial-extras Geo3D spatial context (Geo3dShape.relate, RecursivePrefixTreeStrategy, CompositeSpatialStrategy) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-614.html](../../bugs/LFV-614.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a geo3d box whose top and bottom latitude are equal and which spans all longitudes, for example GeoBBoxFactory.makeGeoBBox(WGS84, 0.5, 0.5, -PI, PI), and expect it to contain exactly the points on that latitude, as the same line over [-179, 179] degrees does. Its isWithin compares a point's z with sin(latitude), the unit-sphere value, with a 1e-10 tolerance, instead of the planet-model plane that the class builds and uses everywhere else, so on WGS84 it rejects every point on its own latitude (and accepts a parallel about 1.2 km away). On any planet model, including the default sphere of the Geo3D spatial context, the 100x looser tolerance makes getRelationship report that the line CONTAINS a rectangle just below it, so a prefix-tree Intersects query for rect(-180, 180, 45.000000001, 45.000000001) matched 4 points at latitudes 10, 10, 30 and 44 degrees, while the [-179, 179] control matched none.

The test shows that the geo3d box `GeoBBoxFactory.makeGeoBBox(WGS84, 0.5, 0.5, -PI, PI)` (zero height, all longitudes; a `GeoDegenerateLatitudeZone`) rejects a point on its own latitude.

## Expected vs actual

Expected `isWithin(new GeoPoint(WGS84, 0.5, 1.0))` to be true, as it is for the same box over longitudes [-3, 3]. Actual: false, because `isWithin` compares z with sin(latitude) instead of using the zone's WGS84 plane.

## Files

- [src/org/apache/lucene/spatial3d/geom/TestGeoZeroHeightFullLongitudeBox.java](src/org/apache/lucene/spatial3d/geom/TestGeoZeroHeightFullLongitudeBox.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/spatial3d/build/libs/lucene-spatial3d-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.spatial3d.geom.TestGeoZeroHeightFullLongitudeBox
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
