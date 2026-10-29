# On WGS84, geo3d path end caps are shifted toward the equator, so Geo3DPoint.newPathQuery and Geo3DDocValuesField.newPathSort miss documents just beyond a poleward path end (sort value +Infinity) and match documents tens of km beyond an equatorward end

Bug LFV-736 · Area: spatial3d / geom path membership (GeoStandardPath end caps; Geo3DPoint.newPathQuery, Geo3DDocValuesField.newPathSort) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-736.html](../../bugs/LFV-736.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run Geo3DPoint.newPathQuery (or Geo3DDocValuesField.newPathSort) on WGS84 with a north-south path, for example from (44N,20E) to (45N,20E), and expect it to match the points within pathWidthMeters of the path, with round end caps of that radius around the first and last points, as it does on PlanetModel.SPHERE. Instead both end caps are shifted about 21 km toward the equator: with pathWidthMeters 20000 the query misses documents 12 and 16 km beyond the north end (sort value +Infinity), and with pathWidthMeters 1000 it matches documents 10 to 40 km south of the first point (sort values 10006 to 40034 m). The end-cap plane (GeoStandardPath.CutoffSingleCircleSegmentEndpoint, built by CircleSegmentEndpoint through the two segment corners with its normal inside the origin-containing cutoff plane) points along the end point's geocentric direction, which on an ellipsoid is not the surface normal there, so the cap circle is centred equatorward of the end point.

on WGS84 a 1 km geo3d path (44N,20E)-(45N,20E) from GeoPathFactory.makeGeoPath (the shape behind Geo3DPoint.newPathQuery) contains (43.82N,20E), 20 km south of its first point, because the end cap is shifted toward the equator.

## Expected vs actual

Expected isWithin = false (about 20 times the path width away); actual true on main 645d4fe14a2 with -ea and -da.

## Files

- [src/org/apache/lucene/spatial3d/geom/TestGeoPathEndCapWGS84.java](src/org/apache/lucene/spatial3d/geom/TestGeoPathEndCapWGS84.java) (JUnit test)

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
     execute --select-class org.apache.lucene.spatial3d.geom.TestGeoPathEndCapWGS84
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
