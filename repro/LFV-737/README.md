# GeoExactCircle.computeOutsideDistance never measures the slice end points where two of its approximation slices meet, so a point outside whose nearest edge point is such a joint gets +Infinity or the distance to the far side of the circle: on WGS84 every point due north or due south of the centre, at any radius; on the sphere a point 0.001 rad due west of a 1e-4 rad exact circle gets 0.0012

Bug LFV-737 · Area: spatial3d / geom exact-circle outside distance (GeoExactCircle.outsideDistance, reached through GeoCircleFactory.makeExactGeoCircle and GeoOutsideDistance.computeOutsideDistance) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-737.html](../../bugs/LFV-737.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build an exact circle on the default WGS84 planet model (GeoCircleFactory.makeExactGeoCircle, centre latitude 0.3 and longitude 0.2, radius 0.1 rad, about 637 km, accuracy 1e-6) and ask computeOutsideDistance for a point 0.11 rad due north of the centre, expecting 'the distance from a point to the closest outside edge' (GeoOutsideDistance), about 0.0096. Instead you get +Infinity, and the same for every point due north or due south at any distance; on the unit sphere a point 0.001 rad due west of a 1e-4 rad exact circle gets 0.0012, the distance to the far side of the circle. The cause: GeoExactCircle.outsideDistance takes the minimum of each slice's plane distance bounded to that slice's sector and never measures the slice end points, so when the nearest edge point is a joint between two slices, the point's foot on each neighbouring plane falls just outside its sector, both return +Infinity, and the minimum is +Infinity or a foot on the far side.

## Expected vs actual

- Expected: Each meridian point's outside distance is at most its distance to the shape's own edge point (0.009558 for 0.11 north); the sphere point 0.001 west gets 0.001.
- Actual: All six meridian points get Infinity; the sphere point gets 0.001199999997484277; the control passes.

## Files

- [src/org/apache/lucene/spatial3d/geom/TestGeoExactCircleOutsideDistanceAtSliceJoints.java](src/org/apache/lucene/spatial3d/geom/TestGeoExactCircleOutsideDistanceAtSliceJoints.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/spatial3d/build/libs/lucene-spatial3d-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.spatial3d.geom.TestGeoExactCircleOutsideDistanceAtSliceJoints
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestGeoExactCircleOutsideDistanceAtSliceJoints.java](TestGeoExactCircleOutsideDistanceAtSliceJoints.java)
- [TestExactCircleOutsideDistanceJointsV1.java](TestExactCircleOutsideDistanceJointsV1.java)
- [TestExactCircleJointStyles.java](TestExactCircleJointStyles.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
