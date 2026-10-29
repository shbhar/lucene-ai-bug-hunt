# On WGS84 and other non-spherical planet models, the outside distance from a point to a geo3d circle smaller than about 50 km is +Infinity in most directions (GeoCircle.computeOutsideDistance with the ARC or LINEAR style, and so Geo3DDocValuesField.newOutsideDistanceSort, which then sorts valid documents as if they had no value), because Plane.arcDistance and Plane.linearDistance look for the edge on the line where the circle's plane meets a plane through the origin, the plane normal and the point, and on an ellipsoid that line misses a small circle

Bug LFV-274 · Area: spatial3d / geom outside distance (Plane.arcDistance and Plane.linearDistance behind GeoStandardCircle and GeoExactCircle computeOutsideDistance; user-facing Geo3DDocValuesField.newOutsideDistanceSort via Geo3DPointOutsideDistanceComparator) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-274.html](../../bugs/LFV-274.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a 1 km circle on the default WGS84 planet model (GeoCircleFactory.makeGeoCircle, or Geo3DDocValuesField.newOutsideDistanceSort with maxRadiusMeters 1000) and ask for the outside distance of points 3 km from the centre, expecting about 2 km in every direction (0.0 inside, distance to the edge outside). Instead computeOutsideDistance returns +Infinity at 10 of 12 bearings (only due north and due south are right), and the sort gives most documents outside the circle +Infinity, the value the javadoc reserves for documents missing the field, so they sort last. The cause: Plane.arcDistance and Plane.linearDistance look for the nearest edge point on the line where the circle's plane meets a plane through the origin, the plane normal and the point; on a sphere that line passes through the circle's centre, but on an ellipsoid the circle plane's normal is tilted, the line passes about 21 km from the circle at latitude 40, and for a smaller circle it finds no edge point at all, so the minimum keeps its +Infinity start value.

The test shows that on WGS84, `GeoCircle.computeOutsideDistance(ARC)` from a point 3 km due east of the centre of a 1 km circle (40N 74W) is +Infinity.

## Expected vs actual

Expected: about 2000 m (the distance to the circle edge; the fixes give 1998.03 m); actual: `expected:<2000.0> but was:<Infinity>`.

## Files

- [src/org/apache/lucene/spatial3d/geom/TestGeoCircleOutsideDistanceWGS84.java](src/org/apache/lucene/spatial3d/geom/TestGeoCircleOutsideDistanceWGS84.java) (JUnit test)

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
     execute --select-class org.apache.lucene.spatial3d.geom.TestGeoCircleOutsideDistanceWGS84
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
