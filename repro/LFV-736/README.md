# On WGS84, geo3d path end caps are shifted toward the equator, so Geo3DPoint.newPathQuery and Geo3DDocValuesField.newPathSort miss documents just beyond a poleward path end (sort value +Infinity) and match documents tens of km beyond an equatorward end

Bug LFV-736 · Area: spatial3d / geom path membership (GeoStandardPath end caps; Geo3DPoint.newPathQuery, Geo3DDocValuesField.newPathSort) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-736.html](../../bugs/LFV-736.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run Geo3DPoint.newPathQuery (or Geo3DDocValuesField.newPathSort) on WGS84 with a north-south path, for example from (44N,20E) to (45N,20E), and expect it to match the points within pathWidthMeters of the path, with round end caps of that radius around the first and last points, as it does on PlanetModel.SPHERE. Instead both end caps are shifted about 21 km toward the equator: with pathWidthMeters 20000 the query misses documents 12 and 16 km beyond the north end (sort value +Infinity), and with pathWidthMeters 1000 it matches documents 10 to 40 km south of the first point (sort values 10006 to 40034 m). The end-cap plane (GeoStandardPath.CutoffSingleCircleSegmentEndpoint, built by CircleSegmentEndpoint through the two segment corners with its normal inside the origin-containing cutoff plane) points along the end point's geocentric direction, which on an ellipsoid is not the surface normal there, so the cap circle is centred equatorward of the end point.

## Expected vs actual

- Expected: Documents within 20 km of the nearest end (5, 12 and 16 km north, 10 km south) match; 30 and 40 km south do not.
- Actual: 12 and 16 km north are missed (newPathSort +Infinity); 30 and 40 km south match (newPathSort 30023 and 40034 m).

## Files

- [src/org/apache/lucene/spatial3d/TestGeo3DPathPolewardEndCap.java](src/org/apache/lucene/spatial3d/TestGeo3DPathPolewardEndCap.java) (JUnit test)

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
     execute --select-class org.apache.lucene.spatial3d.TestGeo3DPathPolewardEndCap
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestGeo3DPathEndCaps.java](TestGeo3DPathEndCaps.java)
- [TestGeo3DPathEndCapShift.java](TestGeo3DPathEndCapShift.java)
- [EndCapReach.java](EndCapReach.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
